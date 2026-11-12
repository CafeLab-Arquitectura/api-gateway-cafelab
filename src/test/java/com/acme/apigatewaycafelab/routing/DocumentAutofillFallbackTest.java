package com.acme.apigatewaycafelab.routing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * Si document-autofill no responde, el CircuitBreaker responde con su fallback (503).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "authorization.jwt.secret=" + DocumentAutofillRouteTest.JWT_SECRET,
                // Puerto 1: nadie escucha, la conexión se rechaza.
                "DOCUMENT_AUTOFILL_URL=http://localhost:1"
        })
class DocumentAutofillFallbackTest {

    @LocalServerPort
    private int port;

    @Test
    void returnsFallbackWhenServiceIsDownTest() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .post().uri("/api/v1/documents/autofill")
                .header("Authorization", "Bearer " + DocumentAutofillRouteTest.jwt(5L))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("document-autofill-cafelab")
                .jsonPath("$.message").exists();
    }
}
