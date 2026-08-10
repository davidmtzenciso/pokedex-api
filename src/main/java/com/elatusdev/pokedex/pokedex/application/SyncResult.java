package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import java.util.Objects;

// created is what the contract turns into 201 + Location versus 200. The use case decides
// it because the use case is the only thing that knows whether a row existed beforehand;
// asking the controller to infer it from the aggregate would be a guess.
public record SyncResult(Pokemon pokemon, boolean created) {

    public SyncResult {
        Objects.requireNonNull(pokemon, "pokemon");
    }
}
