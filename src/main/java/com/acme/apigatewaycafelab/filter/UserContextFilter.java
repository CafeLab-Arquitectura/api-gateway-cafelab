package com.acme.apigatewaycafelab.filter;

import com.acme.apigatewaycafelab.client.UserProfileResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Headers inyectados hacia los microservicios:
 * <ul>
 *   <li>{@code X-User-Email} — subject del JWT (email)</li>
 *   <li>{@code X-User-Id} — claim {@code userId} del JWT o id de perfil resuelto vía iam-service</li>
 * </ul>
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    private final UserProfileResolver userProfileResolver;

    public UserContextFilter(UserProfileResolver userProfileResolver) {
        this.userProfileResolver = userProfileResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> enrichRequest(exchange, chain, auth))
                // Rutas públicas no tienen principal: se dejan pasar sin modificar
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> enrichRequest(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        String email = jwt.getSubject();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        Mono<Long> userIdMono = JwtUserIdExtractor.extractUserId(jwt)
                .map(Mono::just)
                .orElseGet(() -> userProfileResolver.resolveUserIdByEmail(email, authorization));

        return userIdMono
                .map(userId -> exchange.getRequest().mutate()
                        .header("X-User-Email", email)
                        .header("X-User-Id", String.valueOf(userId))
                        .build())
                .defaultIfEmpty(exchange.getRequest().mutate()
                        .header("X-User-Email", email)
                        .build())
                .flatMap(mutatedRequest -> chain.filter(
                        exchange.mutate().request(mutatedRequest).build()));
    }

    @Override
    public int getOrder() {
        // Valor negativo para ejecutarse después de que Spring Security validó el token
        // pero antes de que el Gateway enrute la petición
        return -1;
    }
}
