package com.elatusdev.pokedex.identity.application;

import com.elatusdev.pokedex.identity.domain.RefreshToken;
import com.elatusdev.pokedex.identity.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// REQUIRES_NEW, and that is the entire reason this class exists rather than being a private
// method on the use case.
//
// Reuse is signalled by throwing. The caller runs in a transaction, so that exception marks
// it rollback-only and undoes the revocation the same request just wrote — the API answers
// 401 while every token in the family stays live, including the successor the thief holds.
// Detection without containment is worse than neither, because it looks handled.
//
// An independent transaction commits the revocation before the rejection unwinds. A separate
// bean is required: a self-invoked @Transactional method does not pass through the proxy and
// the annotation would be silently ignored.
@Component
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokens;

    RefreshTokenFamilyRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(String familyId, Instant at) {
        List<RefreshToken> family = refreshTokens.findByFamilyId(familyId);
        refreshTokens.saveAll(family.stream().map(token -> token.revoke(at)).toList());
        return family.size();
    }
}
