package com.elatusdev.pokedex.support;

import com.elatusdev.pokedex.domain.model.RefreshToken;
import com.elatusdev.pokedex.domain.port.RefreshTokenRepository;
import com.elatusdev.pokedex.domain.vo.RefreshTokenId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

// TEST SCOPE ONLY, and temporary — see InMemoryUserStore. No work unit currently owns a
// RefreshTokenRepository adapter: WU-US03-A names only JpaUserRepositoryAdapter, while the
// refresh_tokens table sits in that stream's V1 schema. That gap is reported, not papered
// over: this fake exists so the auth branch can prove its behaviour end to end.
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
