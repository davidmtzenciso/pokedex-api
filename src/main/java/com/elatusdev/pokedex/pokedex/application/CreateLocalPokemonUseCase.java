package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateLocalPokemonUseCase {

    private final PokemonRepository repository;

    public CreateLocalPokemonUseCase(PokemonRepository repository) {
        this.repository = repository;
    }

    public Pokemon create(CreateLocalPokemonCommand command) {
        command.pokeApiId().ifPresent(this::rejectAlreadyReplicated);
        Pokemon created = Pokemon.draft(replicatedFrom(command));
        command.region().ifPresent(created::assignRegion);
        command.notes().ifPresent(created::annotate);
        command.tags().forEach(created::addTag);
        // F6 — DRAFT is exactly the set of unlinked records, so a supplied upstream id has
        // to move the record to PENDING rather than leave a linked DRAFT behind
        command.pokeApiId().ifPresent(created::linkToUpstream);
        return repository.save(created);
    }

    private void rejectAlreadyReplicated(PokeApiId pokeApiId) {
        if (repository.existsByPokeApiId(pokeApiId)) {
            throw new DuplicatePokemonException(pokeApiId);
        }
    }

    // a hand-created record has no upstream to describe it: no abilities, stats, types,
    // evolution links or upstream names until a sync fills them in
    private static ReplicatedFields replicatedFrom(CreateLocalPokemonCommand command) {
        return new ReplicatedFields(
                command.name(),
                command.category(),
                command.mass(),
                command.height(),
                0,
                Sprite.NONE,
                command.description(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
