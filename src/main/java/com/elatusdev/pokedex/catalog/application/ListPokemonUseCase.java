package com.elatusdev.pokedex.catalog.application;

import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.catalog.domain.CatalogPage;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.LocalReplica;

// Deliberately NOT @Transactional: the catalogue call is remote I/O, and holding a database
// transaction open across it is the pattern that exhausts the pool under a slow upstream.
@Service
public class ListPokemonUseCase {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private final PokemonCatalog catalog;
    private final LocalReplica repository;
    private final UpstreamOutagePolicy outagePolicy;

    public ListPokemonUseCase(
            PokemonCatalog catalog, LocalReplica repository, UpstreamOutagePolicy outagePolicy) {
        this.catalog = catalog;
        this.repository = repository;
        this.outagePolicy = outagePolicy;
    }

    public PokemonPageResult list(int page, int size) {
        requireValidPageRequest(page, size);
        return outagePolicy.applyTo(() -> fromCatalogue(page, size), () -> fromReplica(page, size));
    }

    private PokemonPageResult fromCatalogue(int page, int size) {
        CatalogPage upstream = catalog.fetchPage(page, size);
        return new PokemonPageResult(upstream.rows(), page, size, upstream.totalCount(), false);
    }

    // AC-US01-5 and AC-US01-6 — a stale answer beats no answer, but only when there is
    // something local to serve
    private Optional<PokemonPageResult> fromReplica(int page, int size) {
        List<CatalogPokemon> local = repository.findPage(page, size);
        return local.isEmpty()
                ? Optional.empty()
                : Optional.of(new PokemonPageResult(local, page, size, repository.count(), true));
    }

    // rejected, never clamped — a clamped page lets a caller believe it read everything
    private static void requireValidPageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidPaginationException(
                    "page must be >= 0 and size must be between 1 and " + MAX_SIZE, page, size);
        }
    }
}
