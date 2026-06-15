package com.acme.apigatewaycafelab.filter;

import com.acme.apigatewaycafelab.client.UserProfileResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Inyecta contexto de usuario hacia los microservicios a partir del JWT validado por Spring Security.
 * <ul>
 *   <li>{@code X-User-Email} — claim {@code sub} (email)</li>
 *   <li>{@code X-User-Id} — claim {@code userId} / {@code user_id} ({@code profiles.id} emitido por IAM al sign-in)</li>
 * </ul>
 * Los headers {@code X-User-*} enviados por el cliente se descartan; solo confía en el token.
 * Si el JWT no trae {@code userId} (tokens legacy), {@link UserProfileResolver} consulta IAM como fallback.
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    static final String HEADER_USER_EMAIL = "X-User-Email";
    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_PROFILE_ID = "X-Profile-Id";
    private static final String COSTING_PATH_PREFIX = "/api/v1/costing";

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private final UserProfileResolver userProfileResolver;

    public UserContextFilter(UserProfileResolver userProfileResolver) {
        this.userProfileResolver = userProfileResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> enrichRequest(exchange, chain, auth))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.FALSE)))
                .then();
    }

    private Mono<Boolean> enrichRequest(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken auth) {
        Jwt jwt = auth.getToken();
        String email = jwt.getSubject();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean costingRequest = isCostingRequest(exchange.getRequest());

        return resolveUserId(jwt, email, authorization)
                .map(userId -> mutateRequest(exchange.getRequest(), email, userId, costingRequest))
                .defaultIfEmpty(mutateRequest(exchange.getRequest(), email, null, costingRequest))
                .flatMap(mutatedRequest -> chain.filter(
                        exchange.mutate().request(mutatedRequest).build()).thenReturn(Boolean.TRUE));
    }

    private Mono<Long> resolveUserId(Jwt jwt, String email, String authorization) {
        var userIdFromJwt = JwtUserIdExtractor.extractUserId(jwt);
        if (userIdFromJwt.isPresent()) {
            log.debug("Using userId {} from JWT for {}", userIdFromJwt.get(), email);
            return Mono.just(userIdFromJwt.get());
        }

        log.debug("JWT for {} has no userId claim; falling back to IAM profile lookup", email);
        return userProfileResolver.resolveUserIdByEmail(email, authorization);
    }

    private ServerHttpRequest mutateRequest(
            ServerHttpRequest request,
            String email,
            Long userId,
            boolean costingRequest) {
        return request.mutate().headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_EMAIL);
            headers.remove(HEADER_PROFILE_ID);
            headers.set(HEADER_USER_EMAIL, email);
            if (userId != null) {
                headers.set(HEADER_USER_ID, String.valueOf(userId));
                if (costingRequest) {
                    headers.set(HEADER_PROFILE_ID, String.valueOf(userId));
                }
            }
        }).build();
    }

    private boolean isCostingRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path != null && (path.equals(COSTING_PATH_PREFIX) || path.startsWith(COSTING_PATH_PREFIX + "/"));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
