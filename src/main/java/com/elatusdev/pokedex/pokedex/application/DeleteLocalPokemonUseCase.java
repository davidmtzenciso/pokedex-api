package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The delete is hard and irreversible: no tombstone, no deleted_at predicate on every
// query, and re-sync restores replicated fields but never the proprietary ones (ADR-0010).
@Service
@Transactional
public class DeleteLocalPokemonUseCase {

    private final PokemonRepository repository;

    public DeleteLocalPokemonUseCase(PokemonRepository repository) {
        this.repository = repository;
    }

    public void delete(PokemonId id) {
        // checked rather than assumed: deleting nothing would otherwise answer 204 and tell
        // the caller a row was removed when none was
        repository.findById(id).orElseThrow(() -> new PokemonNotFoundException(id));
        repository.delete(id);
    }
}
