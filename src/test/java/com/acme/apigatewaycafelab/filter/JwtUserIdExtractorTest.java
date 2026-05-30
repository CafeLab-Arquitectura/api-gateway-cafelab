package com.acme.apigatewaycafelab.filter;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUserIdExtractorTest {

    @Test
    void readsUserIdClaimTest() {
        var jwt = jwtWithClaims(Map.of("userId", 42));

        assertEquals(java.util.Optional.of(42L), JwtUserIdExtractor.extractUserId(jwt));
    }

    @Test
    void readsSnakeCaseUserIdClaimTest() {
        var jwt = jwtWithClaims(Map.of("user_id", "7"));

        assertEquals(java.util.Optional.of(7L), JwtUserIdExtractor.extractUserId(jwt));
    }

    @Test
    void returnsEmptyWhenClaimMissingTest() {
        var jwt = jwtWithClaims(Map.of("sub", "user@test.com"));

        assertTrue(JwtUserIdExtractor.extractUserId(jwt).isEmpty());
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS512")
                .claims(existing -> existing.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
