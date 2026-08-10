package com.elatusdev.pokedex.web.controller;

import com.elatusdev.pokedex.application.usecase.ListPokemonUseCase;
import com.elatusdev.pokedex.web.api.PokemonApi;
import com.elatusdev.pokedex.web.dto.PokemonPageDTO;
import com.elatusdev.pokedex.web.mapper.PokemonWebMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// Binds, delegates, maps. The pagination policy, the fan-out and the stale fallback all
// live behind the use case; OA1 asserts this implements the generated interface.
@RestController
public class PokemonController implements PokemonApi {

    private final ListPokemonUseCase listPokemon;
    private final PokemonWebMapper mapper;

    public PokemonController(ListPokemonUseCase listPokemon, PokemonWebMapper mapper) {
        this.listPokemon = listPokemon;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PokemonPageDTO> listPokemon(Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toPage(listPokemon.list(page, size)));
    }
}
