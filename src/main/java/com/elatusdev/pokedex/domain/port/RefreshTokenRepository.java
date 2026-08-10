package com.elatusdev.pokedex.domain.port;

import com.elatusdev.pokedex.domain.model.RefreshToken;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByJti(String jti);

    // family revocation reads the family and writes it back revoked, rather than issuing a
    // bulk UPDATE: I8 is a domain rule, and a rule expressed in SQL is a rule no domain test
    // can exercise
    List<RefreshToken> findByFamilyId(String familyId);

    RefreshToken save(RefreshToken token);

    List<RefreshToken> saveAll(List<RefreshToken> tokens);
}
