package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.api.SyncApi;
import com.elatusdev.pokedex.contract.dto.LocalPokemonDTO;
import com.elatusdev.pokedex.contract.dto.SyncBatchRequestDTO;
import com.elatusdev.pokedex.contract.dto.SyncBatchSummaryDTO;
import com.elatusdev.pokedex.pokedex.application.BatchSyncSummary;
import com.elatusdev.pokedex.pokedex.application.BatchSyncUseCase;
import com.elatusdev.pokedex.pokedex.application.SyncPokemonUseCase;
import com.elatusdev.pokedex.pokedex.application.SyncResult;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// Bind, delegate, map. Every status decision here is a translation of something the use
// case already decided — the controller never works out whether a record was created.
@RestController
public class SyncController implements SyncApi {

    private final SyncPokemonUseCase syncPokemon;
    private final BatchSyncUseCase batchSync;
    private final LocalPokemonDtoMapper mapper;

    SyncController(SyncPokemonUseCase syncPokemon, BatchSyncUseCase batchSync, LocalPokemonDtoMapper mapper) {
        this.syncPokemon = syncPokemon;
        this.batchSync = batchSync;
        this.mapper = mapper;
    }

    // 201 with Location when the record is new, 200 when an existing one was refreshed.
    // created comes from the use case because it is the only thing that knows whether a row
    // existed before the merge; inferring it here from the aggregate would be a guess.
    @Override
    public ResponseEntity<LocalPokemonDTO> syncPokemon(String idOrName) {
        SyncResult result = syncPokemon.sync(idOrName);
        LocalPokemonDTO body = mapper.toDto(result.pokemon());
        return result.created()
                ? ResponseEntity.created(locationOf(result)).body(body)
                : ResponseEntity.ok(body);
    }

    // 202, not 200. The batch is accepted and partially complete: some ids replicated, some
    // failed, some had nothing to do. 200 would claim all of it worked, and the summary in
    // the body would be contradicting the status line.
    @Override
    public ResponseEntity<SyncBatchSummaryDTO> syncPokemonBatch(SyncBatchRequestDTO request) {
        BatchSyncSummary summary = batchSync.sync(request.getFrom(), request.getTo());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SyncBatchSummaryDTO(
                        summary.succeeded(), summary.failed(), summary.skipped(), summary.failedIds()));
    }

    private static URI locationOf(SyncResult result) {
        return URI.create("/v1/pokedex/local/"
                + result.pokemon().id().map(PokemonId::value).orElseThrow());
    }
}
