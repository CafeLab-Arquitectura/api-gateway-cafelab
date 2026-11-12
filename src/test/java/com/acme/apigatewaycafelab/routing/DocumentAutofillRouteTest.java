package com.acme.apigatewaycafelab.routing;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ruta /api/v1/documents/** hacia document-autofill-cafelab (URL fija, sin Eureka).
 * Un servidor HTTP local hace de document-autofill y registra lo que recibe.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "authorization.jwt.secret=" + DocumentAutofillRouteTest.JWT_SECRET
        })
class DocumentAutofillRouteTest {

    // 64 bytes: HS512, igual que en producción. Solo para tests.
    static final String JWT_SECRET = "test-only-document-autofill-route-secret-0123456789abcdef0123456";
    private static final int TEN_MB = 10 * 1024 * 1024;

    private static final HttpServer fakeAutofill;
    private static final AtomicInteger requests = new AtomicInteger();
    private static final AtomicLong receivedBytes = new AtomicLong();
    private static final AtomicReference<String> receivedUserId = new AtomicReference<>();
    private static final AtomicReference<String> receivedPath = new AtomicReference<>();
    private static final AtomicReference<String> receivedContentType = new AtomicReference<>();

    static {
        try {
            fakeAutofill = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        fakeAutofill.createContext("/", exchange -> {
            requests.incrementAndGet();
            receivedUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try (InputStream body = exchange.getRequestBody()) {
                receivedBytes.set(body.transferTo(OutputStreamDiscard.INSTANCE));
            }
            byte[] response = "{\"extractionId\":null,\"cached\":false,\"fields\":{},\"warnings\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        fakeAutofill.start();
    }

    @DynamicPropertySource
    static void autofillUrl(DynamicPropertyRegistry registry) {
        registry.add("DOCUMENT_AUTOFILL_URL",
                () -> "http://localhost:" + fakeAutofill.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAutofill() {
        fakeAutofill.stop(0);
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        requests.set(0);
        receivedBytes.set(0);
        receivedUserId.set(null);
        receivedPath.set(null);
        receivedContentType.set(null);
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void forwardsTenMegabyteMultipartWithUserIdFromJwtTest() {
        client.post().uri("/api/v1/documents/autofill")
                .header("Authorization", "Bearer " + jwt(5L))
                .header("X-User-Id", "999") // el cliente no puede elegir su userId
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartWithFile(TEN_MB)))
                .exchange()
                .expectStatus().isOk();

        assertEquals(1, requests.get());
        assertEquals("/api/v1/documents/autofill", receivedPath.get());
        assertEquals("5", receivedUserId.get());
        assertTrue(receivedContentType.get().startsWith("multipart/form-data"));
        assertTrue(receivedBytes.get() > TEN_MB,
                "el servicio debe recibir el archivo completo, recibió " + receivedBytes.get() + " bytes");
    }

    @Test
    void rejectsRequestWithoutJwtTest() {
        client.post().uri("/api/v1/documents/autofill")
                .header("X-User-Id", "5")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartWithFile(1024)))
                .exchange()
                .expectStatus().isUnauthorized();

        assertEquals(0, requests.get());
    }

    private static org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> multipartWithFile(int size) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(new byte[size]) {
            @Override
            public String getFilename() {
                return "guia.pdf";
            }
        }).contentType(MediaType.APPLICATION_PDF);
        return builder.build();
    }

    static String jwt(long userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("user@test.com")
                    .claim("userId", userId)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
            token.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
            return token.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Descarta lo que se escribe; solo interesa contar los bytes recibidos. */
    private static final class OutputStreamDiscard extends java.io.OutputStream {
        static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    }
}
