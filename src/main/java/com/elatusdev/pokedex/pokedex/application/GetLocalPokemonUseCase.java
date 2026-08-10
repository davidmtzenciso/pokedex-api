package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetLocalPokemonUseCase {

    private final PokemonRepository repository;

    public GetLocalPokemonUseCase(PokemonRepository repository) {
        this.repository = repository;
    }

    public Pokemon get(PokemonId id) {
        return repository.findById(id).orElseThrow(() -> new PokemonNotFoundException(id));
    }
}
