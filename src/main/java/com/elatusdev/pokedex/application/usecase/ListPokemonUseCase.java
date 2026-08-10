package com.elatusdev.pokedex.application.usecase;

import com.elatusdev.pokedex.application.result.PokemonPageResult;
import com.elatusdev.pokedex.domain.exception.InvalidPaginationException;
import com.elatusdev.pokedex.domain.exception.UpstreamTimeoutException;
import com.elatusdev.pokedex.domain.exception.UpstreamUnavailableException;
import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.port.CatalogPage;
import com.elatusdev.pokedex.domain.port.PokemonCatalog;
import com.elatusdev.pokedex.domain.port.PokemonRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Deliberately NOT @Transactional: the catalogue call is remote I/O, and holding a database
// transaction open across it is the pattern that exhausts the pool under a slow upstream.
@Service
public class ListPokemonUseCase {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(ListPokemonUseCase.class);

    private final PokemonCatalog catalog;
    private final PokemonRepository repository;

    public ListPokemonUseCase(PokemonCatalog catalog, PokemonRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    public PokemonPageResult list(int page, int size) {
        requireValidPageRequest(page, size);
        try {
            CatalogPage upstream = catalog.fetchPage(page, size);
            return new PokemonPageResult(upstream.rows(), page, size, upstream.totalCount(), false);
        } catch (UpstreamUnavailableException | UpstreamTimeoutException outage) {
            return fallBackToReplica(page, size, outage);
        }
    }

    // AC-US01-5 and AC-US01-6 — a stale answer beats no answer, but only when there is
    // something local to serve
    private PokemonPageResult fallBackToReplica(int page, int size, RuntimeException outage) {
        List<Pokemon> local = repository.findPage(page, size);
        if (local.isEmpty()) {
            throw outage;
        }
        log.warn("serving page {} from the local replica: {}", page, outage.getMessage());
        return new PokemonPageResult(local, page, size, repository.count(), true);
    }

    // rejected, never clamped — a clamped page lets a caller believe it read everything
    private static void requireValidPageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidPaginationException(
                    "page must be >= 0 and size must be between 1 and " + MAX_SIZE, page, size);
        }
    }
}
