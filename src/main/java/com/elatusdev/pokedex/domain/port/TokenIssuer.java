// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.port;

import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

// no PII in a claim: signed is not encrypted, so the subject is an id and never an email
public interface TokenIssuer {

    String issueAccessToken(UserId subject, Set<Role> roles, Instant issuedAt);

    String issueRefreshToken(UserId subject, String familyId, Instant issuedAt);

    Optional<UserId> verify(String token, Instant now);
}
