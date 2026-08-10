package com.elatusdev.pokedex.shared.domain;

import java.net.URI;
import java.util.Optional;

public record Sprite(URI frontDefault, URI officialArtwork) {

    public static final Sprite NONE = new Sprite(null, null);

    public Optional<URI> preferred() {
        return Optional.ofNullable(officialArtwork).or(() -> Optional.ofNullable(frontDefault));
    }
}
