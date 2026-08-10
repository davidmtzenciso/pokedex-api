// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.vo;

import java.net.URI;
import java.util.Optional;

public record Sprite(URI frontDefault, URI officialArtwork) {

    public static final Sprite NONE = new Sprite(null, null);

    public Optional<URI> preferred() {
        return Optional.ofNullable(officialArtwork).or(() -> Optional.ofNullable(frontDefault));
    }
}
