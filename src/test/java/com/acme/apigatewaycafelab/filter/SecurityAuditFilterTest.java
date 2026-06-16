package com.acme.apigatewaycafelab.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SecurityAuditFilterTest {

    private final SecurityAuditFilter filter = new SecurityAuditFilter();

    @Test
    void logsUnauthorizedWithoutBreakingResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/recipes").build());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        };

        assertDoesNotThrow(() -> filter.filter(exchange, chain).block());
    }

    @Test
    void ignoresSuccessfulResponses() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/recipes").build());
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        assertDoesNotThrow(() -> filter.filter(exchange, chain).block());
    }
}
