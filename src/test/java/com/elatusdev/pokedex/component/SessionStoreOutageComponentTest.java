package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

// AC-AUTH-6 and risk R14, proven against a real outage rather than a mocked exception:
// the session store fails CLOSED, so losing Redis costs access and never grants it.
//
// Its own container and its own context, because the test ends by destroying the container
// and would take every other component test down with it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
// This test ends by destroying the container its context is wired to, so that context must
// not be cached and handed to a later test — the connection pool would keep retrying a dead
// port. Observed as a flaky 400 on an unrelated endpoint two runs out of three.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionStoreOutageComponentTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private final String baseUrl;
    private final RestClient client = RestClient.create();

    SessionStoreOutageComponentTest(@Autowired Environment environment) {
        this.baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    @Test
    void should_return_401_and_never_200_when_the_session_store_is_unreachable() {
        register();
        String access = login();
        // the token works while the store is up, which is what makes the second half of
        // this test mean anything
        assertThat(get("/v1/security/me", access).getStatusCode().value()).isEqualTo(200);

        REDIS.stop();

        ResponseEntity<String> duringOutage = get("/v1/security/me", access);

        assertThat(duringOutage.getStatusCode().value()).isEqualTo(401);
        assertThat(duringOutage.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type ->
                        assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        // the outage is reported as a closed session rather than as its own code: whether
        // Redis is reachable is not something an unauthenticated caller gets to probe
        assertThat(json(duringOutage.getBody())).containsEntry("code", "TOKEN_REVOKED");
    }

    private void register() {
        ResponseEntity<String> response = post(
                "/v1/security/register",
                "{\"username\":\"outage\",\"email\":\"outage@elatus-dev.com\",\"password\":\"Outage123!secure\"}",
                null);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    private String login() {
        ResponseEntity<String> response =
                post("/v1/security/login", "{\"username\":\"outage\",\"password\":\"Outage123!secure\"}", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (String) json(response.getBody()).get("accessToken");
    }

    private static Map<String, Object> json(String body) {
        return JsonMapper.builder().build().readValue(body, Map.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return client.get()
                .uri(baseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // the status is the assertion
                })
                .toEntity(String.class);
    }

    private ResponseEntity<String> post(String path, String body, String token) {
        return client.post()
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // the status is the assertion
                })
                .toEntity(String.class);
    }
}
