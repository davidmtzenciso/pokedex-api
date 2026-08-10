package com.elatusdev.pokedex.infrastructure.security;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.elatusdev.pokedex.domain.model.IssuedToken;
import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.TokenType;
import com.elatusdev.pokedex.domain.model.VerifiedToken;
import com.elatusdev.pokedex.domain.port.TokenIssuer;
import com.elatusdev.pokedex.domain.vo.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ES256 with the key held privately: a verifier holds only the public half and therefore
// cannot mint tokens. Algorithm and kid are allow-listed on verify, which closes the
// alg:none attack and RS256-to-HS256 key confusion in the same check.
public class Es256TokenIssuer implements TokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(Es256TokenIssuer.class);

    private static final String ALGORITHM = "ES256";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "tkn";
    private static final String CLAIM_FAMILY = "fam";

    private final PrivateKey signingKey;
    private final PublicKey verificationKey;
    private final JwtProperties properties;

    public Es256TokenIssuer(PrivateKey signingKey, PublicKey verificationKey, JwtProperties properties) {
        this.signingKey = signingKey;
        this.verificationKey = verificationKey;
        this.properties = properties;
    }

    @Override
    public IssuedToken issueAccessToken(UserId subject, Set<Role> roles, Instant issuedAt) {
        List<String> names = roles.stream().map(Role::name).sorted().toList();
        return sign(subject, issuedAt, properties.accessTtl(), TokenType.ACCESS, CLAIM_ROLES, names);
    }

    @Override
    public IssuedToken issueRefreshToken(UserId subject, String familyId, Instant issuedAt) {
        return sign(subject, issuedAt, properties.refreshTtl(), TokenType.REFRESH, CLAIM_FAMILY, familyId);
    }

    @Override
    public Optional<VerifiedToken> verify(String token, Instant now) {
        try {
            return Optional.of(read(token, now));
        } catch (JwtException | IllegalArgumentException e) {
            // the security audit line of §9.5. The token itself is never logged: it is a
            // bearer credential, and a rejected one is still valid somewhere else
            log.warn("security: token rejected — {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private IssuedToken sign(
            UserId subject, Instant issuedAt, Duration ttl, TokenType type, String claim, Object value) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                .header()
                .keyId(properties.keyId())
                .and()
                .issuer(properties.issuer())
                .audience()
                .add(properties.audience())
                .and()
                .subject(Long.toString(subject.value()))
                .id(jti)
                .claim(CLAIM_TYPE, type.name())
                .claim(claim, value)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.ES256)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    private VerifiedToken read(String token, Instant now) {
        Claims claims = Jwts.parser()
                .keyLocator(allowListedKey())
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .clock(() -> Date.from(now))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new VerifiedToken(
                TokenType.valueOf(claims.get(CLAIM_TYPE, String.class)),
                UserId.of(Long.parseLong(claims.getSubject())),
                claims.getId(),
                rolesOf(claims),
                Optional.ofNullable(claims.get(CLAIM_FAMILY, String.class)),
                claims.getExpiration().toInstant());
    }

    // one key today, resolved by kid so rotation is a configuration change rather than a
    // redeploy of every consumer. An unrecognised kid is rejected rather than defaulted.
    private Locator<Key> allowListedKey() {
        return header -> {
            if (!ALGORITHM.equals(header.getAlgorithm()) || !properties.keyId().equals(header.get("kid"))) {
                throw new SignatureException("unexpected algorithm or key id");
            }
            return verificationKey;
        };
    }

    private static Set<Role> rolesOf(Claims claims) {
        List<?> names = claims.get(CLAIM_ROLES, List.class);
        return names == null
                ? Set.of()
                : names.stream().map(String::valueOf).map(Role::valueOf).collect(toUnmodifiableSet());
    }
}
