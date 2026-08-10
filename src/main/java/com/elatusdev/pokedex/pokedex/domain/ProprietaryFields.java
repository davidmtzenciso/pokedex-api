package com.elatusdev.pokedex.pokedex.domain;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// The Proprietary half of the partition in WF-000 §4.7. The curator is the authority for
// every field here, so re-sync must leave all of them byte-identical (F7 / AC5). The two
// records share no component, which is what makes that merge total rather than a policy.
public record ProprietaryFields(
        Optional<Region> region,
        Optional<Notes> notes,
        Optional<UserId> curatedBy,
        List<Tag> tags,
        List<LocalizedName> curatorNames) {

    public static final int MAX_TAGS = 10;

    public ProprietaryFields {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(notes, "notes");
        Objects.requireNonNull(curatedBy, "curatedBy");
        tags = List.copyOf(tags);
        curatorNames = List.copyOf(curatorNames);
        requireAllCurator(curatorNames);
    }

    public static ProprietaryFields none() {
        return new ProprietaryFields(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return region.isEmpty() && notes.isEmpty() && curatedBy.isEmpty() && tags.isEmpty() && curatorNames.isEmpty();
    }

    public ProprietaryFields withRegion(Region newRegion) {
        return new ProprietaryFields(Optional.of(newRegion), notes, curatedBy, tags, curatorNames);
    }

    public ProprietaryFields withNotes(Notes newNotes) {
        return new ProprietaryFields(region, Optional.of(newNotes), curatedBy, tags, curatorNames);
    }

    public ProprietaryFields withCurator(UserId curator) {
        return new ProprietaryFields(region, notes, Optional.of(curator), tags, curatorNames);
    }

    public ProprietaryFields withTags(List<Tag> newTags) {
        return new ProprietaryFields(region, notes, curatedBy, newTags, curatorNames);
    }

    private static void requireAllCurator(List<LocalizedName> names) {
        if (names.stream().anyMatch(localized -> localized.source() != NameSource.CURATOR)) {
            throw new InvalidPokemonDataException("every proprietary localized name must have source CURATOR");
        }
    }
}
