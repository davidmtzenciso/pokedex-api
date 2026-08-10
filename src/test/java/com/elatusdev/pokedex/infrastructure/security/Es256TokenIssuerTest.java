package com.elatusdev.pokedex.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.domain.model.IssuedToken;
import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.TokenType;
import com.elatusdev.pokedex.domain.model.VerifiedToken;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class Es256TokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UserId SUBJECT = UserId.of(7);
    private static final String ISSUER = "https://pokedex.elatus-dev.com";
    private static final String AUDIENCE = "pokedex-api";
    private static final String KID = "pokedex-dev-1";
    private static final String FAMILY = "family-1";

    private static KeyPair ours;
    private static KeyPair theirs;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ours = generator.generateKeyPair();
        theirs = generator.generateKeyPair();
    }

    private static JwtProperties properties(String issuer, String audience, String keyId) {
        return new JwtProperties(
                null, null, "pokedex-dev", keyId, issuer, audience, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private static Es256TokenIssuer issuer() {
        return new Es256TokenIssuer(ours.getPrivate(), ours.getPublic(), properties(ISSUER, AUDIENCE, KID));
    }

    private static Map<String, Object> segment(String token, int index) {
        String json = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[index]), StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readValue(json, Map.class);
    }

    @Test
    void should_round_trip_an_access_token_with_its_subject_roles_and_identifier() {
        IssuedToken issued = issuer().issueAccessToken(SUBJECT, Set.of(Role.CURATOR, Role.ADMIN), NOW);

        VerifiedToken verified = issuer().verify(issued.token(), NOW).orElseThrow();

        assertThat(verified.type()).isEqualTo(TokenType.ACCESS);
        assertThat(verified.subject()).isEqualTo(SUBJECT);
        assertThat(verified.jti()).isEqualTo(issued.jti());
        assertThat(verified.roles()).containsExactlyInAnyOrder(Role.CURATOR, Role.ADMIN);
        assertThat(verified.familyId()).isEmpty();
        assertThat(verified.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void should_round_trip_a_refresh_token_carrying_its_family_and_no_roles() {
        IssuedToken issued = issuer().issueRefreshToken(SUBJECT, FAMILY, NOW);

        VerifiedToken verified = issuer().verify(issued.token(), NOW).orElseThrow();

        assertThat(verified.type()).isEqualTo(TokenType.REFRESH);
        assertThat(verified.subject()).isEqualTo(SUBJECT);
        assertThat(verified.jti()).isEqualTo(issued.jti());
        assertThat(verified.familyId()).contains(FAMILY);
        assertThat(verified.roles()).isEmpty();
        assertThat(verified.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void should_give_every_token_a_distinct_identifier() {
        Es256TokenIssuer issuer = issuer();

        IssuedToken first = issuer.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);
        IssuedToken second = issuer.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    // AC-AUTH-4 and ADR-0005: signed is not encrypted, so the claim set is pinned exactly.
    // Asserting the whole key set is what catches a future field being added by accident —
    // asserting "does not contain the email" would not notice a new one.
    @Test
    void should_carry_no_personal_data_in_an_access_token() {
        IssuedToken issued = issuer().issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(segment(issued.token(), 1))
                .containsOnlyKeys("iss", "aud", "sub", "jti", "roles", "tkn", "iat", "exp")
                .containsEntry("sub", "7")
                .containsEntry("iss", ISSUER)
                .containsEntry("tkn", "ACCESS");
    }

    @Test
    void should_carry_no_personal_data_in_a_refresh_token() {
        IssuedToken issued = issuer().issueRefreshToken(SUBJECT, FAMILY, NOW);

        assertThat(segment(issued.token(), 1))
                .containsOnlyKeys("iss", "aud", "sub", "jti", "fam", "tkn", "iat", "exp")
                .containsEntry("tkn", "REFRESH");
    }

    @Test
    void should_name_the_algorithm_and_the_key_in_the_header() {
        IssuedToken issued = issuer().issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(segment(issued.token(), 0)).containsEntry("alg", "ES256").containsEntry("kid", KID);
    }

    @Test
    void should_reject_a_token_signed_by_a_key_we_do_not_hold() {
        Es256TokenIssuer other =
                new Es256TokenIssuer(theirs.getPrivate(), theirs.getPublic(), properties(ISSUER, AUDIENCE, KID));
        IssuedToken forged = other.issueAccessToken(SUBJECT, Set.of(Role.ADMIN), NOW);

        assertThat(issuer().verify(forged.token(), NOW)).isEmpty();
    }

    // the classic: strip the signature and claim the token is unsecured
    @Test
    void should_reject_a_token_that_claims_algorithm_none() {
        String header = base64Url("{\"alg\":\"none\",\"kid\":\"" + KID + "\"}");
        String payload = base64Url("{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE
                + "\",\"sub\":\"7\",\"jti\":\"forged\",\"tkn\":\"ACCESS\",\"exp\":4102444800}");

        assertThat(issuer().verify(header + "." + payload + ".", NOW)).isEmpty();
    }

    @Test
    void should_reject_a_token_minted_for_another_audience() {
        Es256TokenIssuer other =
                new Es256TokenIssuer(ours.getPrivate(), ours.getPublic(), properties(ISSUER, "another-api", KID));
        IssuedToken misaddressed = other.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(issuer().verify(misaddressed.token(), NOW)).isEmpty();
    }

    @Test
    void should_reject_a_token_minted_by_another_issuer() {
        Es256TokenIssuer other = new Es256TokenIssuer(
                ours.getPrivate(), ours.getPublic(), properties("https://evil.example.com", AUDIENCE, KID));
        IssuedToken foreign = other.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(issuer().verify(foreign.token(), NOW)).isEmpty();
    }

    // AC-AUTH-3 — keys are resolved by kid, so a token naming a key we do not have is a 401
    @Test
    void should_reject_a_token_naming_an_unknown_key() {
        Es256TokenIssuer other = new Es256TokenIssuer(
                ours.getPrivate(), ours.getPublic(), properties(ISSUER, AUDIENCE, "rotated-away-2019"));
        IssuedToken stale = other.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(issuer().verify(stale.token(), NOW)).isEmpty();
    }

    @Test
    void should_reject_a_token_once_it_has_expired() {
        Es256TokenIssuer issuer = issuer();
        IssuedToken issued = issuer.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW);

        assertThat(issuer.verify(issued.token(), NOW.plus(Duration.ofMinutes(14)))).isPresent();
        assertThat(issuer.verify(issued.token(), NOW.plus(Duration.ofMinutes(16)))).isEmpty();
    }

    @Test
    void should_reject_a_token_that_is_not_a_token_at_all() {
        Es256TokenIssuer issuer = issuer();

        assertThat(issuer.verify("not.a.jwt", NOW)).isEmpty();
        assertThat(issuer.verify("", NOW)).isEmpty();
    }

    @Test
    void should_reject_a_token_whose_claims_have_been_rewritten() {
        String forged = tamperType(issuer().issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW));

        assertThat(issuer().verify(forged, NOW)).isEmpty();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // rewriting a claim invalidates the signature, which is the point: the type is protected
    // by the same signature as everything else
    private static String tamperType(IssuedToken issued) {
        String[] parts = issued.token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return parts[0] + "." + base64Url(payload.replace("\"ACCESS\"", "\"SUPERUSER\"")) + "." + parts[2];
    }

    @Test
    void should_expire_an_issued_token_at_the_configured_horizon() {
        Es256TokenIssuer issuer = issuer();

        assertThat(issuer.issueAccessToken(SUBJECT, Set.of(Role.CURATOR), NOW).expiresAt())
                .isEqualTo(NOW.plusSeconds(900));
        assertThat(issuer.issueRefreshToken(SUBJECT, FAMILY, NOW).expiresAt())
                .isEqualTo(NOW.plusSeconds(604_800));
    }

    @Test
    void should_reject_a_refresh_token_whose_family_was_stripped() {
        Optional<VerifiedToken> verified = issuer().verify("a.b.c", NOW);

        assertThat(verified).isEmpty();
    }
}
