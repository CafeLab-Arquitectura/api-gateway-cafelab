package com.acme.apigatewaycafelab.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Header inyectado: X-User-Email  → email del usuario autenticado
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    String email = auth.getToken().getSubject();

                    var mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Email", email)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                // Rutas públicas no tienen principal: se dejan pasar sin modificar
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Valor negativo para ejecutarse después de que Spring Security validó el token
        // pero antes de que el Gateway enrute la petición
        return -1;
    }
}
