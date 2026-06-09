package com.acme.apigatewaycafelab.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserProfileResolverTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolvesUserIdFromSingleProfileObjectResponse() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = startServer("{\"id\":8,\"email\":\"user@test.com\"}", authorizationHeader);
        UserProfileResolver resolver = newResolver();

        Long userId = resolver.resolveUserIdByEmail("user@test.com", "Bearer token").block();

        assertEquals(8L, userId);
        assertEquals("Bearer token", authorizationHeader.get());
    }

    @Test
    void resolvesUserIdFromProfileArrayResponse() throws Exception {
        server = startServer("[{\"id\":8,\"email\":\"user@test.com\"}]", new AtomicReference<>());
        UserProfileResolver resolver = newResolver();

        Long userId = resolver.resolveUserIdByEmail("user@test.com", "Bearer token").block();

        assertEquals(8L, userId);
    }

    @Test
    void returnsEmptyWhenProfilePayloadHasNoId() throws Exception {
        server = startServer("{\"email\":\"user@test.com\"}", new AtomicReference<>());
        UserProfileResolver resolver = newResolver();

        Long userId = resolver.resolveUserIdByEmail("user@test.com", "Bearer token").block();

        assertNull(userId);
    }

    private UserProfileResolver newResolver() {
        return new UserProfileResolver(
                "http://localhost:" + server.getAddress().getPort(),
                0,
                Duration.ofMillis(10),
                Duration.ofMillis(10),
                Duration.ofSeconds(2),
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
                new ObjectMapper()
        );
    }

    private HttpServer startServer(String body, AtomicReference<String> authorizationHeader) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/profiles", exchange -> handleProfilesRequest(exchange, body, authorizationHeader));
        httpServer.start();
        return httpServer;
    }

    private void handleProfilesRequest(
            HttpExchange exchange,
            String body,
            AtomicReference<String> authorizationHeader) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }
}
