package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.catalog.application.GetPokemonDetailUseCase;
import com.elatusdev.pokedex.catalog.application.ListPokemonUseCase;
import com.elatusdev.pokedex.contract.api.PokemonApi;
import com.elatusdev.pokedex.contract.dto.PokemonDetailDTO;
import com.elatusdev.pokedex.contract.dto.PokemonPageDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// Binds, delegates, maps. The pagination policy, the fan-out and the stale fallback all
// live behind the use case; OA1 asserts this implements the generated interface.
@RestController
public class PokemonController implements PokemonApi {

    private final ListPokemonUseCase listPokemon;
    private final GetPokemonDetailUseCase getPokemonDetail;
    private final PokemonWebMapper mapper;

    public PokemonController(
            ListPokemonUseCase listPokemon, GetPokemonDetailUseCase getPokemonDetail, PokemonWebMapper mapper) {
        this.listPokemon = listPokemon;
        this.getPokemonDetail = getPokemonDetail;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PokemonPageDTO> listPokemon(Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toPage(listPokemon.list(page, size)));
    }

    @Override
    public ResponseEntity<PokemonDetailDTO> getPokemon(String idOrName) {
        return ResponseEntity.ok(mapper.toDetail(getPokemonDetail.detail(idOrName)));
    }
}
