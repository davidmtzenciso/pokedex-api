package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateLocalPokemonUseCase {

    private final PokemonRepository repository;
    private final ClockPort clock;

    public UpdateLocalPokemonUseCase(PokemonRepository repository, ClockPort clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Pokemon update(PokemonId id, UpdateLocalPokemonCommand command) {
        Pokemon stored = repository.findById(id).orElseThrow(() -> new PokemonNotFoundException(id));
        requireCurrentVersion(id, stored, command);
        apply(stored, command);
        markCustomised(stored, clock.now());
        return repository.save(stored);
    }

    // checked here rather than left to @Version alone: this turns the common case — a
    // client editing a record it read a while ago — into a deterministic 412 instead of a
    // race that only sometimes loses
    private static void requireCurrentVersion(PokemonId id, Pokemon stored, UpdateLocalPokemonCommand command) {
        if (stored.version() != command.version()) {
            throw new OptimisticLockingFailureException("Pokemon " + id.value() + " has changed since it was read");
        }
    }

    private static void apply(Pokemon stored, UpdateLocalPokemonCommand command) {
        command.region().ifPresent(stored::assignRegion);
        command.notes().ifPresent(stored::annotate);
        List.copyOf(stored.tags()).forEach(stored::removeTag);
        command.tags().forEach(stored::addTag);
    }

    // DRAFT and PENDING have no legal edge to CUSTOMIZED, and a draft is still editable —
    // so this transitions only from the states that can, rather than throwing on the rest
    private static void markCustomised(Pokemon stored, Instant at) {
        if (stored.replicationState().canTransitionTo(ReplicationState.CUSTOMIZED)) {
            stored.transitionTo(ReplicationState.CUSTOMIZED, at);
        }
    }
}
