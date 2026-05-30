package com.acme.apigatewaycafelab.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserProfileResolver {

    private final WebClient iamWebClient;
    private final Map<String, Long> cacheByEmail = new ConcurrentHashMap<>();

    public UserProfileResolver(@Value("${IAM_SERVICE_URL}") String iamServiceUrl) {
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
                .onErrorResume(ex -> Mono.empty());
    }
}
