package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Proprietary fields only, by construction. A replicated field cannot be expressed here at
// all, which is a stronger guarantee than validating one away: the curator is not that
// field's authority, and any edit would be overwritten by the next re-sync (R13).
//
// version is mandatory — it is what makes a lost update detectable rather than silent.
public record UpdateLocalPokemonCommand(
        long version,
        Optional<Region> region,
        Optional<Notes> notes,
        List<Tag> tags,
        List<LocalizedName> curatorNames) {

    public UpdateLocalPokemonCommand {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(notes, "notes");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        curatorNames = List.copyOf(Objects.requireNonNull(curatorNames, "curatorNames"));
    }
}
