package com.acme.apigatewaycafelab.filter;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

final class JwtUserIdExtractor {

    private JwtUserIdExtractor() {
    }

    static Optional<Long> extractUserId(Jwt jwt) {
        for (String claimName : new String[]{"userId", "user_id"}) {
            Optional<Long> parsed = parseClaim(jwt.getClaim(claimName));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private static Optional<Long> parseClaim(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
