package com.acme.apigatewaycafelab.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Registra eventos de seguridad sospechosos en un logger dedicado (SECURITY_AUDIT).
 *
 * Cubre el Quality Attribute Scenario de Seguridad:
 * "genera un registro (log) del evento sospechoso"
 *
 * Eventos capturados:
 *   401 UNAUTHORIZED  — request sin token o con token inválido/expirado
 *   403 FORBIDDEN     — token válido pero sin permiso sobre el recurso
 *   429 TOO_MANY_REQ  — IP/usuario superó el rate limit (posible fuerza bruta)
 */
@Component
public class SecurityAuditFilter implements WebFilter {

    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .doFinally(signal -> {
                    var response = exchange.getResponse();
                    if (response.getStatusCode() == null) return;

                    int status = response.getStatusCode().value();
                    if (status != 401 && status != 403 && status != 429) return;

                    ServerHttpRequest req = exchange.getRequest();

                    String ip = Optional.ofNullable(req.getHeaders().getFirst("X-Forwarded-For"))
                            .map(h -> h.split(",")[0].trim())
                            .or(() -> Optional.ofNullable(req.getRemoteAddress())
                                    .map(addr -> addr.getAddress().getHostAddress()))
                            .orElse("unknown");

                    String userAgent = Optional
                            .ofNullable(req.getHeaders().getFirst(HttpHeaders.USER_AGENT))
                            .orElse("-");

                    String eventType = switch (status) {
                        case 401 -> "UNAUTHORIZED_ACCESS";
                        case 403 -> "FORBIDDEN_ACCESS";
                        case 429 -> "RATE_LIMIT_EXCEEDED";
                        default  -> "SECURITY_EVENT";
                    };

                    AUDIT.warn("[{}] status={} ip={} method={} path={} ua=\"{}\"",
                            eventType,
                            status,
                            ip,
                            req.getMethod(),
                            req.getPath().value(),
                            userAgent);
                });
    }
}
