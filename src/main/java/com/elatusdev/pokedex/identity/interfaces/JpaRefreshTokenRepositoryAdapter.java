package com.elatusdev.pokedex.identity.interfaces;

import com.elatusdev.pokedex.identity.domain.RefreshToken;
import com.elatusdev.pokedex.identity.domain.RefreshTokenRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// The runtime behind refresh rotation and family revocation. Until this existed the table
// was reachable only by a test fake, so I8 held in tests and nowhere else.
//
// Writes flush eagerly: ux_refresh_tokens_jti is how a replayed jti is detected, and a
// violation deferred to commit surfaces outside the caller's try block.
@Component
@Transactional(readOnly = true)
public class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;
    private final RefreshTokenPersistenceMapper mapper;

    JpaRefreshTokenRepositoryAdapter(
            RefreshTokenJpaRepository jpaRepository, RefreshTokenPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RefreshToken> findByJti(String jti) {
        return jpaRepository.findByJti(jti).map(mapper::toDomain);
    }

    @Override
    public List<RefreshToken> findByFamilyId(String familyId) {
        return jpaRepository.findByFamilyId(familyId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toDataModel(token)));
    }

    // one flush for the whole family: revocation is a single decision, and a partial sweep
    // would leave a live token behind exactly when theft has been detected
    @Override
    @Transactional
    public List<RefreshToken> saveAll(List<RefreshToken> tokens) {
        return jpaRepository.saveAllAndFlush(tokens.stream().map(mapper::toDataModel).toList()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
