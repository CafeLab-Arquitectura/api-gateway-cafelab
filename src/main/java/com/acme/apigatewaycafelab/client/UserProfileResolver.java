package com.acme.apigatewaycafelab.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(UserProfileResolver.class);

    private final WebClient iamWebClient;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Map<String, Long> cacheByEmail = new ConcurrentHashMap<>();

    public UserProfileResolver(
            @Value("${IAM_SERVICE_URL}") String iamServiceUrl,
            @Value("${cafelab.gateway.iam-lookup.retries:4}") int maxRetries,
            @Value("${cafelab.gateway.iam-lookup.initial-backoff:3s}") Duration initialBackoff,
            @Value("${cafelab.gateway.iam-lookup.max-backoff:20s}") Duration maxBackoff) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.iamWebClient = WebClient.builder()
                .baseUrl(iamServiceUrl)
                .build();
    }

    public Mono<Long> resolveUserIdByEmail(String email, String authorizationHeader) {
        if (email == null || email.isBlank()) {
            return Mono.empty();
        }

        Long cached = cacheByEmail.get(email);
        if (cached != null) {
            return Mono.just(cached);
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Mono.empty();
        }

        return iamWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/profiles")
                        .queryParam("email", email)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .bodyToFlux(ProfileResponse.class)
                .next()
                .map(ProfileResponse::id)
                .doOnNext(userId -> cacheByEmail.put(email, userId))
                .retryWhen(Retry.backoff(maxRetries, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .filter(this::isTransientFailure)
                        .doBeforeRetry(signal -> log.warn(
                                "IAM profile lookup failed for {} (attempt {}), retrying: {}",
                                email,
                                signal.totalRetries() + 1,
                                signal.failure().toString())))
                .onErrorResume(ex -> {
                    log.warn("IAM profile lookup exhausted retries for {}", email, ex);
                    return Mono.empty();
                });
    }

    private boolean isTransientFailure(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true;
        }
        if (throwable instanceof WebClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.is5xxServerError();
        }
        return false;
    }
}
