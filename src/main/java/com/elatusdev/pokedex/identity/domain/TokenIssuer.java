package com.elatusdev.pokedex.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

// no PII in a claim: signed is not encrypted, so the subject is an id and never an email
public interface TokenIssuer {

    IssuedToken issueAccessToken(UserId subject, Set<Role> roles, Instant issuedAt);

    IssuedToken issueRefreshToken(UserId subject, String familyId, Instant issuedAt);

    Optional<VerifiedToken> verify(String token, Instant now);
}
