package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

// I11 / AC-AUTH-1: every mutating endpoint requires a principal, and the deny-by-default
// chain is asserted route by route rather than assumed from reading the config.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthEnforcementComponentTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static String baseUrl;

    private final RestClient client = RestClient.create();

    AuthEnforcementComponentTest(@Autowired Environment environment) {
        baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    @BeforeAll
    static void registerDemoUser() {
        // deliberately not @BeforeEach: registration is idempotent only once, and a 409 on
        // the second run would mask a real failure
    }

    // --- public routes -----------------------------------------------------------------

    @Test
    void should_serve_the_published_contract_without_a_token() {
        assertThat(get("/v3/api-docs.yaml", null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void should_serve_the_health_probe_without_a_token() {
        assertThat(get("/actuator/health", null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void should_reach_login_without_a_token_and_reject_the_credentials() {
        ResponseEntity<String> response = post("/v1/security/login", "{\"username\":\"nobody\",\"password\":\"x\"}", null);

        // 401 rather than 403: the route is public, the credentials are not valid
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("INVALID_CREDENTIALS");
    }

    // --- protected routes --------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "GET,/v1/security/me",
        "POST,/v1/security/logout",
        "POST,/v1/pokedex/local",
        "PATCH,/v1/pokedex/local/1",
        "DELETE,/v1/pokedex/local/1",
        "POST,/v1/pokedex/sync/1",
        "GET,/v1/pokedex/anything-not-enumerated"
    })
    void should_reject_every_protected_route_without_a_token(String method, String path) {
        ResponseEntity<String> response = exchange(HttpMethod.valueOf(method), path, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
        assertThat(codeOf(response)).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void should_reject_a_token_that_claims_algorithm_none() {
        String header = base64Url("{\"alg\":\"none\",\"kid\":\"pokedex-test-1\"}");
        String payload = base64Url("{\"sub\":\"1\",\"jti\":\"forged\",\"tkn\":\"ACCESS\",\"exp\":4102444800}");

        ResponseEntity<String> response = get("/v1/security/me", header + "." + payload + ".");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void should_reject_a_token_that_is_not_a_token() {
        ResponseEntity<String> response = get("/v1/security/me", "garbage");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("INVALID_TOKEN");
    }

    // --- the full journey ---------------------------------------------------------------

    @Test
    void should_register_log_in_and_answer_me_then_stop_answering_after_logout() {
        String username = "journey";
        register(username, "journey@elatus-dev.com");
        Map<String, Object> tokens = login(username);
        String access = (String) tokens.get("accessToken");

        ResponseEntity<String> me = get("/v1/security/me", access);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(json(me.getBody()))
                .containsEntry("username", username)
                .containsEntry("email", "journey@elatus-dev.com");
        assertThat(me.getBody()).doesNotContain("$2a$");

        assertThat(post("/v1/security/logout", "", access).getStatusCode().value())
                .isEqualTo(204);

        // AC-AUTH-5 — the token is otherwise still well within its exp
        ResponseEntity<String> afterLogout = get("/v1/security/me", access);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(afterLogout)).isEqualTo("TOKEN_REVOKED");
    }

    @Test
    void should_reject_a_second_registration_of_the_same_username() {
        register("duplicate", "duplicate@elatus-dev.com");

        ResponseEntity<String> again = post(
                "/v1/security/register",
                "{\"username\":\"duplicate\",\"email\":\"other@elatus-dev.com\",\"password\":\"Duplicate123!\"}",
                null);

        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(again)).isEqualTo("USER_ALREADY_EXISTS");
    }

    // AC-AUTH-2 — replaying a rotated refresh token revokes the entire family
    @Test
    void should_revoke_the_family_when_a_rotated_refresh_token_is_replayed() {
        register("rotator", "rotator@elatus-dev.com");
        String firstRefresh = (String) login("rotator").get("refreshToken");

        ResponseEntity<String> rotated = post("/v1/security/token/refresh", refreshBody(firstRefresh), null);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        String secondRefresh = (String) json(rotated.getBody()).get("refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        ResponseEntity<String> replay = post("/v1/security/token/refresh", refreshBody(firstRefresh), null);
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(replay)).isEqualTo("TOKEN_REUSE_DETECTED");

        // the successor dies with the family: that is what "revoke the whole family" means
        ResponseEntity<String> successor = post("/v1/security/token/refresh", refreshBody(secondRefresh), null);
        assertThat(successor.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(successor)).isEqualTo("TOKEN_REUSE_DETECTED");
    }

    @Test
    void should_reject_an_access_token_presented_for_rotation() {
        register("confused", "confused@elatus-dev.com");
        String access = (String) login("confused").get("accessToken");

        ResponseEntity<String> response = post("/v1/security/token/refresh", refreshBody(access), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void should_reject_a_refresh_token_presented_as_a_bearer_credential() {
        register("bearer", "bearer@elatus-dev.com");
        String refresh = (String) login("bearer").get("refreshToken");

        ResponseEntity<String> response = get("/v1/security/me", refresh);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("INVALID_TOKEN");
    }

    // --- helpers -------------------------------------------------------------------------

    private void register(String username, String email) {
        ResponseEntity<String> response = post(
                "/v1/security/register",
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"Component123!\"}".formatted(username, email),
                null);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    private Map<String, Object> login(String username) {
        ResponseEntity<String> response = post(
                "/v1/security/login",
                "{\"username\":\"%s\",\"password\":\"Component123!\"}".formatted(username),
                null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response.getBody());
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"%s\"}".formatted(token);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> json(String body) {
        return JsonMapper.builder().build().readValue(body, Map.class);
    }

    private static String codeOf(ResponseEntity<String> response) {
        return (String) json(response.getBody()).get("code");
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    private ResponseEntity<String> post(String path, String body, String token) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String body, String token) {
        RestClient.RequestBodySpec request = client.method(method)
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.body(body);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    // the status is the assertion; the default handler would throw first
                })
                .toEntity(String.class);
    }
}
