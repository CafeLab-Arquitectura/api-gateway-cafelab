package com.acme.apigatewaycafelab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Debe coincidir con authorization.jwt.secret del iam-service
    @Value("${authorization.jwt.secret}")
    private String jwtSecret;

    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ServerBearerTokenAuthenticationConverter defaultConverter = new ServerBearerTokenAuthenticationConverter();
        ServerAuthenticationConverter publicAwareConverter = exchange -> {
            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();
            if ((HttpMethod.POST.equals(method) && "/api/v1/profiles".equals(path))
                    || path.startsWith("/api/v1/authentication/")
                    || (HttpMethod.GET.equals(method) && path.startsWith("/api/v1/coffee-lots"))
                    || "/actuator/health".equals(path)
                    || path.startsWith("/fallback/")) {
                return Mono.empty();
            }
            return defaultConverter.convert(exchange);
        };

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Preflight CORS — el navegador envía OPTIONS antes de cada request
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Rutas públicas del IAM ──────────────────────────────
                        .pathMatchers(HttpMethod.POST, "/api/v1/authentication/sign-in").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/authentication/sign-up").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/profiles").permitAll()

                        // ── Rutas públicas de consulta de lotes (para IoT y backend interno) ──
                        .pathMatchers(HttpMethod.GET, "/api/v1/coffee-lots", "/api/v1/coffee-lots/**").permitAll()

                        // ── Health check de Actuator ────────────────────────────
                        .pathMatchers("/actuator/health").permitAll()

                        // ── Fallbacks del Circuit Breaker (forward interno) ──────
                        .pathMatchers("/fallback/**").permitAll()

                        // ── Todo lo demás requiere JWT válido ───────────────────
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(publicAwareConverter)
                        .jwt(Customizer.withDefaults())
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl, "http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        /*
         * El iam-service firma con Keys.hmacShaKeyFor() de JJWT.
         * Para producción usa una clave de 64 bytes → JJWT elige HS512.
         * Asegúrate de que JWT_SECRET tenga exactamente 64 bytes en Azure.
         *
         * Clave local de desarrollo (del iam-service):
         *   local-dev-only-change-me-0123456789abcdef0123456789abcdef
         *   → 57 bytes → JJWT usa HS384 con esa clave.
         *   Para evitar el mismatch en local, usa una clave de 64 bytes en ambos servicios.
         */
        SecretKeySpec key = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"
        );
        return NimbusReactiveJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}
