package com.jumptotech.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full-stack integration test base: a real Spring Boot server (random port)
 * talking to a real PostgreSQL instance via Testcontainers, exercised over
 * real HTTP - including Spring Security's session cookie and CSRF handling,
 * not a mocked servlet environment.
 *
 * The Postgres container uses the Testcontainers "singleton container"
 * pattern deliberately: it is started once in a static initializer and
 * never registered with the JUnit {@code @Testcontainers}/{@code @Container}
 * lifecycle. That lifecycle stops a shared static container after the
 * *first* test class that declares it finishes - which would kill the
 * database out from under every subsequent subclass sharing this base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The concurrency tests deliberately fire many simultaneous transfer requests at the
        // same account pair; the production default pool size (10) is sized for a single
        // banking app instance, not for a test that intentionally maximizes contention.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "25");
    }

    /**
     * Apache HttpClient5's default connection pool caps at 5 connections per
     * route, which would silently throttle the concurrency tests below the
     * parallelism they're trying to prove out. This raises that cap for the
     * test client only - unrelated to anything the production app ships.
     */
    @TestConfiguration
    static class TestHttpClientConfig {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(50);
            connectionManager.setDefaultMaxPerRoute(50);
            CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplateBuilder().requestFactory(() -> factory);
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Could not parse response body as JSON: " + response.getBody(), e);
        }
    }

    /** A cookie-jar-carrying client that mimics a browser session across requests, CSRF included. */
    protected class Session {
        private final Map<String, String> cookies = new LinkedHashMap<>();

        Session() {
            // Prime the CSRF cookie the way a browser loading the SPA shell would.
            get("/actuator/health");
        }

        public ResponseEntity<String> get(String path) {
            return exchange(HttpMethod.GET, path, null);
        }

        public ResponseEntity<String> post(String path, Object body) {
            return exchange(HttpMethod.POST, path, body);
        }

        public ResponseEntity<String> post(String path, Object body, String idempotencyKey) {
            return exchangeWithHeaders(HttpMethod.POST, path, body, Map.of("Idempotency-Key", idempotencyKey));
        }

        public ResponseEntity<String> patch(String path, Object body) {
            return exchange(HttpMethod.PATCH, path, body);
        }

        private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
            return exchangeWithHeaders(method, path, body, Map.of());
        }

        private ResponseEntity<String> exchangeWithHeaders(HttpMethod method, String path, Object body, Map<String, String> extraHeaders) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!cookies.isEmpty()) {
                headers.add(HttpHeaders.COOKIE, cookies.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("; ")));
            }
            if (method != HttpMethod.GET && cookies.containsKey("XSRF-TOKEN")) {
                headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            extraHeaders.forEach(headers::add);

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(baseUrl() + path, method, entity, String.class);
            captureCookies(response);
            return response;
        }

        /** Immutable copy for use from other threads (e.g. firing concurrent requests). */
        public Map<String, String> cookieSnapshot() {
            return Map.copyOf(cookies);
        }

        private void captureCookies(ResponseEntity<String> response) {
            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies == null) {
                return;
            }
            for (String setCookie : setCookies) {
                String pair = setCookie.split(";", 2)[0];
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    cookies.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        }
    }

    protected Session newSession() {
        return new Session();
    }

    /**
     * Fires one request using a fixed, pre-captured cookie snapshot rather
     * than a shared, mutable {@link Session} - safe to call concurrently
     * from multiple threads, which the per-session cookie jar is not.
     */
    protected ResponseEntity<String> postWithFixedCookies(Map<String, String> cookieSnapshot, String path,
                                                            Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookieSnapshot.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; ")));
        if (cookieSnapshot.containsKey("XSRF-TOKEN")) {
            headers.add("X-XSRF-TOKEN", cookieSnapshot.get("XSRF-TOKEN"));
        }
        if (idempotencyKey != null) {
            headers.add("Idempotency-Key", idempotencyKey);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST, entity, String.class);
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.test";
    }

    protected ResponseEntity<String> register(Session session, String email, String password, String firstName, String lastName) {
        return session.post("/api/v1/auth/register", Map.of(
                "email", email, "password", password, "firstName", firstName, "lastName", lastName, "phone", "555-010-9999"));
    }

    protected ResponseEntity<String> login(Session session, String email, String password) {
        return session.post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    protected static final String DEFAULT_TEST_PASSWORD = "SuperSecret123";

    /** Registers, logs in, and returns the ids of the new customer's [checking, savings] accounts, using a default test password. */
    protected List<UUID> registerLoginAndGetAccountIds(Session session, String email) {
        return registerLoginAndGetAccountIds(session, email, DEFAULT_TEST_PASSWORD);
    }

    /** Registers, logs in, and returns the ids of the new customer's [checking, savings] accounts. */
    protected List<UUID> registerLoginAndGetAccountIds(Session session, String email, String password) {
        register(session, email, password, "Test", "User");
        login(session, email, password);
        ResponseEntity<String> accountsResponse = session.get("/api/v1/accounts");
        JsonNode accounts = json(accountsResponse);
        List<UUID> ids = new java.util.ArrayList<>();
        accounts.forEach(a -> ids.add(UUID.fromString(a.get("id").asText())));
        return ids;
    }
}
