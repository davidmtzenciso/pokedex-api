package com.elatusdev.pokedex.identity.application.usecase;

import com.elatusdev.pokedex.identity.domain.exception.InvalidCredentialsException;
import com.elatusdev.pokedex.identity.domain.model.IssuedToken;
import com.elatusdev.pokedex.identity.domain.model.RefreshToken;
import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.shared.port.ClockPort;
import com.elatusdev.pokedex.identity.domain.port.PasswordHasher;
import com.elatusdev.pokedex.identity.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.identity.domain.port.SessionStore;
import com.elatusdev.pokedex.identity.domain.port.TokenIssuer;
import com.elatusdev.pokedex.identity.domain.port.UserRepository;
import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import com.elatusdev.pokedex.identity.domain.vo.UserId;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticateUserUseCase {

    // a real BCrypt-12 digest of the literal "not-a-real-account". Not a secret — its only
    // job is to cost the same to verify as a real hash, so that an unknown username takes
    // as long to reject as a wrong password and timing stops enumerating accounts.
    static final PasswordHash DECOY =
            new PasswordHash("$2a$12$SC7N2YSI.aKK5X04r4V8i.BEX5bMj7pjyF4rQ5/mfngem3S14nivm");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final TokenIssuer tokenIssuer;
    private final SessionStore sessions;
    private final ClockPort clock;

    public AuthenticateUserUseCase(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordHasher hasher,
            TokenIssuer tokenIssuer,
            SessionStore sessions,
            ClockPort clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.tokenIssuer = tokenIssuer;
        this.sessions = sessions;
        this.clock = clock;
    }

    public TokenPair authenticate(String username, String rawPassword) {
        Instant now = clock.now();
        User user = authenticated(username, rawPassword);
        UserId id = user.id().orElseThrow(InvalidCredentialsException::new);
        // a family per login, so revoking a stolen session does not log the user out of
        // every other device they hold
        String familyId = UUID.randomUUID().toString();
        IssuedToken refresh = tokenIssuer.issueRefreshToken(id, familyId, now);
        refreshTokens.save(RefreshToken.issue(id, familyId, refresh.jti(), refresh.expiresAt()));
        IssuedToken access = tokenIssuer.issueAccessToken(id, user.roles(), now);
        sessions.open(access.jti(), id, access.expiresAt());
        return TokenPair.of(access, refresh, now);
    }

    private User authenticated(String username, String rawPassword) {
        Optional<User> candidate = users.findByUsername(new Username(username));
        // the comparison runs even when there is no such user, against the decoy
        boolean matches = hasher.matches(rawPassword, candidate.map(User::passwordHash).orElse(DECOY));
        return candidate.filter(user -> matches).orElseThrow(InvalidCredentialsException::new);
    }
}
