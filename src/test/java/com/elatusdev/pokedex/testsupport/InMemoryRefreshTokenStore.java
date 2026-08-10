package com.elatusdev.pokedex.testsupport;

import com.elatusdev.pokedex.identity.domain.model.RefreshToken;
import com.elatusdev.pokedex.identity.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.identity.domain.vo.RefreshTokenId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import com.elatusdev.pokedex.identity.infrastructure.persistence.JpaUserRepositoryAdapter;

// TEST SCOPE ONLY, and temporary. No work unit owns a RefreshTokenRepository adapter:
// WU-US03-A delivered JpaUserRepositoryAdapter and a refresh_tokens table — with
// ux_refresh_tokens_jti and an index on (user_id, family_id) — but no adapter behind it.
// The table is there; the code to reach it is not.
//
// This fake exists so the auth stream can prove rotation and family revocation end to end
// rather than leave them unverified. It is deliberately NOT @Primary, so the day a
// JpaRefreshTokenRepositoryAdapter lands, the duplicate bean is a loud startup failure
// instead of a fake quietly outranking the real one. DELETE IT THEN.
//
// Until then, component tests here prove auth behaviour, NOT persistence: nothing rolls
// back, and no test in this class exercises the unique constraint or the cascade.
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenRepository {

    private final Map<String, RefreshToken> byJti = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<RefreshToken> findByJti(String jti) {
        return Optional.ofNullable(byJti.get(jti));
    }

    @Override
    public List<RefreshToken> findByFamilyId(String familyId) {
        return byJti.values().stream()
                .filter(token -> token.familyId().equals(familyId))
                .toList();
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenId id = token.id().orElseGet(() -> RefreshTokenId.of(sequence.incrementAndGet()));
        RefreshToken stored = RefreshToken.rehydrate(
                id, token.userId(), token.familyId(), token.jti(), token.expiresAt(), token.revokedAt());
        byJti.put(stored.jti(), stored);
        return stored;
    }

    @Override
    public List<RefreshToken> saveAll(List<RefreshToken> tokens) {
        return tokens.stream().map(this::save).toList();
    }
}
