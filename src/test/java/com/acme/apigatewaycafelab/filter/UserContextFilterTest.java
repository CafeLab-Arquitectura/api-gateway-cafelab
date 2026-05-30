package com.acme.apigatewaycafelab.filter;

import com.acme.apigatewaycafelab.client.UserProfileResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContextFilterTest {

    @Mock
    private UserProfileResolver userProfileResolver;

    @Mock
    private GatewayFilterChain chain;

    private UserContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter(userProfileResolver);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void injectsUserIdFromJwtWithoutCallingIamTest() {
        Jwt jwt = jwtWithClaims(Map.of("userId", 5L));
        MockServerWebExchange exchange = authenticatedExchange(jwt, "999");

        filter.filter(exchange, chain).block();

        var downstream = captureDownstreamExchange();
        assertEquals("user@test.com", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_EMAIL));
        assertEquals("5", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID));
        verify(userProfileResolver, never()).resolveUserIdByEmail(any(), any());
    }

    @Test
    void stripsClientSpoofedHeadersBeforeInjectingFromJwtTest() {
        Jwt jwt = jwtWithClaims(Map.of("userId", 12L));
        MockServerWebExchange exchange = authenticatedExchange(jwt, "777");

        filter.filter(exchange, chain).block();

        var downstream = captureDownstreamExchange();
        assertEquals("12", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID));
        assertEquals("user@test.com", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_EMAIL));
    }

    @Test
    void fallsBackToIamWhenJwtHasNoUserIdClaimTest() {
        Jwt jwt = jwtWithClaims(Map.of());
        MockServerWebExchange exchange = authenticatedExchange(jwt, null);
        when(userProfileResolver.resolveUserIdByEmail("user@test.com", "Bearer token"))
                .thenReturn(Mono.just(88L));

        filter.filter(exchange, chain).block();

        var downstream = captureDownstreamExchange();
        assertEquals("88", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID));
        verify(userProfileResolver).resolveUserIdByEmail("user@test.com", "Bearer token");
    }

    @Test
    void forwardsEmailOnlyWhenUserIdCannotBeResolvedTest() {
        Jwt jwt = jwtWithClaims(Map.of());
        MockServerWebExchange exchange = authenticatedExchange(jwt, null);
        when(userProfileResolver.resolveUserIdByEmail("user@test.com", "Bearer token"))
                .thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        var downstream = captureDownstreamExchange();
        assertEquals("user@test.com", downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_EMAIL));
        assertNull(downstream.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID));
    }

    @Test
    void passesThroughPublicRoutesWithoutPrincipalTest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/authentication/sign-in").build());

        filter.filter(exchange, chain).block();

        verify(userProfileResolver, never()).resolveUserIdByEmail(any(), any());
        verify(chain).filter(exchange);
    }

    private ServerWebExchange captureDownstreamExchange() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    private static MockServerWebExchange authenticatedExchange(Jwt jwt, String spoofedUserId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/costing/lot-performances")
                .header("Authorization", "Bearer token")
                .header(UserContextFilter.HEADER_USER_EMAIL, "spoof@test.com");
        if (spoofedUserId != null) {
            builder.header(UserContextFilter.HEADER_USER_ID, spoofedUserId);
        }

        return MockServerWebExchange.builder(builder.build())
                .principal(new JwtAuthenticationToken(jwt))
                .build();
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "HS512")
                .subject("user@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
