package com.acme.apigatewaycafelab.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(UserProfileResolver.class);

    private final WebClient iamWebClient;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Map<String, Long> cacheByEmail = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Autowired
    public UserProfileResolver(
            @Value("${IAM_SERVICE_URL}") String iamServiceUrl,
            @Value("${cafelab.gateway.iam-lookup.retries:4}") int maxRetries,
            @Value("${cafelab.gateway.iam-lookup.initial-backoff:3s}") Duration initialBackoff,
            @Value("${cafelab.gateway.iam-lookup.max-backoff:20s}") Duration maxBackoff,
            @Value("${cafelab.gateway.iam-lookup.response-timeout:60s}") Duration responseTimeout,
            ObjectMapper objectMapper) {
        this(
                iamServiceUrl,
                maxRetries,
                initialBackoff,
                maxBackoff,
                responseTimeout,
                HttpClient.create().responseTimeout(responseTimeout),
                objectMapper
        );
    }

    UserProfileResolver(
            String iamServiceUrl,
            int maxRetries,
            Duration initialBackoff,
            Duration maxBackoff,
            Duration responseTimeout,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.iamWebClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(iamServiceUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public Mono<Long> resolveUserIdByEmail(String email, String authorizationHeader) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Mono.empty();
        }

        Long cached = cacheByEmail.get(normalizedEmail);
        if (cached != null) {
            return Mono.just(cached);
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Mono.empty();
        }

        return lookupProfile(normalizedEmail, authorizationHeader)
                .doOnNext(userId -> cacheByEmail.put(normalizedEmail, userId))
                .retryWhen(Retry.backoff(maxRetries, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .filter(this::isTransientFailure)
                        .doBeforeRetry(signal -> log.warn(
                                "IAM profile lookup failed for {} (attempt {}), retrying: {}",
                                normalizedEmail,
                                signal.totalRetries() + 1,
                                signal.failure().toString())))
                .onErrorResume(ex -> {
                    log.warn("IAM profile lookup exhausted retries for {}", normalizedEmail, ex);
                    return Mono.empty();
                });
    }

    private Mono<Long> lookupProfile(String email, String authorizationHeader) {
        return iamWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/profiles")
                        .queryParam("email", email)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(String.class)
                            .flatMap(this::extractUserId);
                });
    }

    private Mono<Long> extractUserId(String payload) {
        try {
            return extractUserIdFromPayload(payload)
                    .map(Mono::just)
                    .orElseGet(Mono::empty);
        } catch (IOException exception) {
            return Mono.error(exception);
        }
    }

    Optional<Long> extractUserIdFromPayload(String payload) throws IOException {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(payload);
        JsonNode profileNode = root;
        if (root.isArray()) {
            if (root.isEmpty()) {
                return Optional.empty();
            }
            profileNode = root.get(0);
        }

        if (profileNode == null || profileNode.isMissingNode()) {
            return Optional.empty();
        }

        JsonNode idNode = profileNode.get("id");
        if (idNode == null || idNode.isNull()) {
            return Optional.empty();
        }
        if (idNode.canConvertToLong()) {
            return Optional.of(idNode.longValue());
        }
        if (idNode.isTextual()) {
            try {
                return Optional.of(Long.parseLong(idNode.textValue()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
