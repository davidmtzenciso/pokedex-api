package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.api.LocalPokemonApi;
import com.elatusdev.pokedex.contract.dto.CreateLocalPokemonRequestDTO;
import com.elatusdev.pokedex.contract.dto.LocalPokemonDTO;
import com.elatusdev.pokedex.contract.dto.LocalPokemonPageDTO;
import com.elatusdev.pokedex.contract.dto.PatchLocalPokemonRequestDTO;
import com.elatusdev.pokedex.contract.dto.RegionDTO;
import com.elatusdev.pokedex.contract.dto.ReplaceLocalPokemonRequestDTO;
import com.elatusdev.pokedex.pokedex.application.CreateLocalPokemonUseCase;
import com.elatusdev.pokedex.pokedex.application.DeleteLocalPokemonUseCase;
import com.elatusdev.pokedex.pokedex.application.GetLocalPokemonUseCase;
import com.elatusdev.pokedex.pokedex.application.ListLocalPokemonUseCase;
import com.elatusdev.pokedex.pokedex.application.UpdateLocalPokemonUseCase;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// Bind, delegate, map. Every status this returns is a success status — the error contract
// belongs to the advices, which is why there is not a single try/catch here.
@RestController
public class LocalPokemonController implements LocalPokemonApi {

    private final ListLocalPokemonUseCase listLocalPokemon;
    private final GetLocalPokemonUseCase getLocalPokemon;
    private final CreateLocalPokemonUseCase createLocalPokemon;
    private final UpdateLocalPokemonUseCase updateLocalPokemon;
    private final DeleteLocalPokemonUseCase deleteLocalPokemon;
    private final LocalPokemonWebMapper mapper;

    public LocalPokemonController(
            ListLocalPokemonUseCase listLocalPokemon,
            GetLocalPokemonUseCase getLocalPokemon,
            CreateLocalPokemonUseCase createLocalPokemon,
            UpdateLocalPokemonUseCase updateLocalPokemon,
            DeleteLocalPokemonUseCase deleteLocalPokemon,
            LocalPokemonWebMapper mapper) {
        this.listLocalPokemon = listLocalPokemon;
        this.getLocalPokemon = getLocalPokemon;
        this.createLocalPokemon = createLocalPokemon;
        this.updateLocalPokemon = updateLocalPokemon;
        this.deleteLocalPokemon = deleteLocalPokemon;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<LocalPokemonPageDTO> listLocalPokemon(
            Integer page, Integer size, RegionDTO region, String tag, String q) {
        LocalPokemonFilter filter = new LocalPokemonFilter(
                Optional.ofNullable(region).map(value -> Region.fromString(value.getValue())),
                Optional.ofNullable(tag).map(Tag::new),
                Optional.ofNullable(q));
        return ResponseEntity.ok(mapper.toPage(listLocalPokemon.list(filter, page, size)));
    }

    @Override
    public ResponseEntity<LocalPokemonDTO> getLocalPokemon(Long id) {
        return ResponseEntity.ok(mapper.toDto(getLocalPokemon.get(PokemonId.of(id))));
    }

    @Override
    public ResponseEntity<LocalPokemonDTO> createLocalPokemon(CreateLocalPokemonRequestDTO body) {
        Pokemon created = createLocalPokemon.create(mapper.toCommand(body));
        LocalPokemonDTO dto = mapper.toDto(created);
        return ResponseEntity.created(URI.create("/v1/pokedex/local/" + dto.getId())).body(dto);
    }

    @Override
    public ResponseEntity<LocalPokemonDTO> replaceLocalPokemon(Long id, ReplaceLocalPokemonRequestDTO body) {
        return ResponseEntity.ok(mapper.toDto(updateLocalPokemon.update(PokemonId.of(id), mapper.toCommand(body))));
    }

    @Override
    public ResponseEntity<LocalPokemonDTO> patchLocalPokemon(Long id, PatchLocalPokemonRequestDTO body) {
        return ResponseEntity.ok(mapper.toDto(updateLocalPokemon.update(PokemonId.of(id), mapper.toCommand(body))));
    }

    @Override
    public ResponseEntity<Void> deleteLocalPokemon(Long id) {
        deleteLocalPokemon.delete(PokemonId.of(id));
        return ResponseEntity.noContent().build();
    }
}
