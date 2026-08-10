package com.elatusdev.pokedex.identity.interfaces;

import com.elatusdev.pokedex.identity.domain.RefreshToken;
import com.elatusdev.pokedex.identity.domain.RefreshTokenId;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceMapper {

    public RefreshToken toDomain(RefreshTokenDataModel model) {
        return RefreshToken.rehydrate(
                RefreshTokenId.of(model.getId()),
                UserId.of(model.getUserId()),
                model.getFamilyId(),
                model.getJti(),
                model.getExpiresAt(),
                Optional.ofNullable(model.getRevokedAt()));
    }

    // a null id is how Hibernate is told to insert; an absent Optional means "not persisted
    // yet", and the two say the same thing across the boundary
    public RefreshTokenDataModel toDataModel(RefreshToken token) {
        return new RefreshTokenDataModel(
                token.id().map(RefreshTokenId::value).orElse(null),
                token.userId().value(),
                token.familyId(),
                token.jti(),
                token.expiresAt(),
                token.revokedAt().orElse(null));
    }
}
