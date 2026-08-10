// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.application.usecase;

import com.elatusdev.pokedex.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.domain.exception.TokenReuseDetectedException;
import com.elatusdev.pokedex.domain.model.IssuedToken;
import com.elatusdev.pokedex.domain.model.RefreshToken;
import com.elatusdev.pokedex.domain.model.TokenType;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.model.VerifiedToken;
import com.elatusdev.pokedex.domain.port.ClockPort;
import com.elatusdev.pokedex.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.domain.port.SessionStore;
import com.elatusdev.pokedex.domain.port.TokenIssuer;
import com.elatusdev.pokedex.domain.port.UserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenRotationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRotationUseCase.class);

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final TokenIssuer tokenIssuer;
    private final SessionStore sessions;
    private final ClockPort clock;

    public RefreshTokenRotationUseCase(
            RefreshTokenRepository refreshTokens,
            UserRepository users,
            TokenIssuer tokenIssuer,
            SessionStore sessions,
            ClockPort clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.tokenIssuer = tokenIssuer;
        this.sessions = sessions;
        this.clock = clock;
    }

    public TokenPair rotate(String presentedToken) {
        Instant now = clock.now();
        RefreshToken presented = locate(presentedToken, now);
        rejectReuse(presented, now);
        rejectExpired(presented, now);
        return issueSuccessor(presented, now);
    }

    private RefreshToken locate(String presentedToken, Instant now) {
        VerifiedToken verified = tokenIssuer
                .verify(presentedToken, now)
                .filter(token -> token.type() == TokenType.REFRESH)
                .orElseThrow(InvalidTokenException::new);
        return refreshTokens.findByJti(verified.jti()).orElseThrow(InvalidTokenException::new);
    }

    // I8 / F11, synchronously: a stolen token must not survive the request that detected it,
    // so this is the one domain event that cannot be deferred to an async handler
    private void rejectReuse(RefreshToken presented, Instant now) {
        if (!presented.isRevoked()) {
            return;
        }
        List<RefreshToken> family = refreshTokens.findByFamilyId(presented.familyId());
        refreshTokens.saveAll(family.stream().map(token -> token.revoke(now)).toList());
        log.warn(
                "security: refresh token reuse detected, revoked {} tokens in family {}",
                family.size(),
                presented.familyId());
        throw new TokenReuseDetectedException(presented.familyId());
    }

    // expiry is not theft: the family survives and the client authenticates again
    private void rejectExpired(RefreshToken presented, Instant now) {
        if (presented.isExpired(now)) {
            throw new InvalidTokenException();
        }
    }

    private TokenPair issueSuccessor(RefreshToken presented, Instant now) {
        User owner = users.findById(presented.userId()).orElseThrow(InvalidTokenException::new);
        refreshTokens.save(presented.revoke(now));
        IssuedToken refresh = tokenIssuer.issueRefreshToken(presented.userId(), presented.familyId(), now);
        refreshTokens.save(
                RefreshToken.issue(presented.userId(), presented.familyId(), refresh.jti(), refresh.expiresAt()));
        IssuedToken access = tokenIssuer.issueAccessToken(presented.userId(), owner.roles(), now);
        sessions.open(access.jti(), presented.userId(), access.expiresAt());
        return TokenPair.of(access, refresh, now);
    }
}
