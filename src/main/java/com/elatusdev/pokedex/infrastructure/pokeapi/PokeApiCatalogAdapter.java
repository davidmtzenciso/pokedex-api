package com.elatusdev.pokedex.infrastructure.pokeapi;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.exception.UpstreamTimeoutException;
import com.elatusdev.pokedex.domain.exception.UpstreamUnavailableException;
import com.elatusdev.pokedex.domain.model.EvolutionLink;
import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.port.PokemonCatalog;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// A page of N costs 1 + 2N upstream calls (IA1, IA2). Concurrency is bounded on two axes:
// the semaphore caps calls in flight, and the page-size cap of 100 caps how many a single
// request can queue — docs/handbook/concurrency.md.
public class PokeApiCatalogAdapter implements PokemonCatalog {

    private static final Logger log = LoggerFactory.getLogger(PokeApiCatalogAdapter.class);

    private final PokeApiClient client;
    private final PokeApiMapper mapper;
    private final EvolutionChainMapper evolutionMapper;
    private final PokeApiProperties properties;

    public PokeApiCatalogAdapter(
            PokeApiClient client,
            PokeApiMapper mapper,
            EvolutionChainMapper evolutionMapper,
            PokeApiProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.evolutionMapper = evolutionMapper;
        this.properties = properties;
    }

    @Override
    public List<Pokemon> fetchPage(int page, int size) {
        List<PokeApiNameRef> refs = fetchListing(page, size).results();
        Semaphore gate = new Semaphore(properties.maxConcurrency());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Optional<Pokemon>>> pending = refs.stream()
                    .map(ref -> CompletableFuture.supplyAsync(() -> summariseQuietly(ref, gate), executor))
                    .toList();
            List<Pokemon> rows =
                    pending.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList();
            logFanOut(page, size, refs.size(), rows.size());
            return rows;
        }
    }

    @Override
    public int totalCount() {
        return fetchListing(0, 1).count();
    }

    @Override
    public Optional<Pokemon> fetchById(PokeApiId pokeApiId) {
        return fetchDetail("/pokemon/" + pokeApiId.value());
    }

    @Override
    public Optional<Pokemon> fetchByName(PokemonName name) {
        return fetchDetail("/pokemon/" + name.value());
    }

    private PokeApiListResponse fetchListing(int page, int size) {
        String path = "/pokemon?offset=" + ((long) page * size) + "&limit=" + size;
        return client.get(path, PokeApiListResponse.class)
                .orElseThrow(() -> new UpstreamUnavailableException("pokeapi returned no listing for " + path, null));
    }

    // one failing row must not fail the page — the alternative is that a single upstream
    // hiccup on row 7 costs the user all 10
    private Optional<Pokemon> summariseQuietly(PokeApiNameRef ref, Semaphore gate) {
        gate.acquireUninterruptibly();
        try {
            return summarise(ref);
        } catch (UpstreamUnavailableException | UpstreamTimeoutException | InvalidPokemonDataException dropped) {
            log.warn("dropping '{}' from the page: {}", ref.name(), dropped.getMessage());
            return Optional.empty();
        } finally {
            gate.release();
        }
    }

    // a list row needs no evolution chain, which is what keeps the page at 1 + 2N
    private Optional<Pokemon> summarise(PokeApiNameRef ref) {
        return client.get("/pokemon/" + PokeApiResourceId.of(ref).value(), PokeApiPokemonResponse.class)
                .map(pokemon -> mapper.toPokemon(pokemon, species(pokemon), List.of()));
    }

    private Optional<Pokemon> fetchDetail(String path) {
        return client.get(path, PokeApiPokemonResponse.class).map(this::withEvolution);
    }

    private Pokemon withEvolution(PokeApiPokemonResponse pokemon) {
        PokeApiSpeciesResponse species = species(pokemon);
        return mapper.toPokemon(pokemon, species, evolution(species));
    }

    private PokeApiSpeciesResponse species(PokeApiPokemonResponse pokemon) {
        String path = "/pokemon-species/" + PokeApiResourceId.of(pokemon.species()).value();
        return client.get(path, PokeApiSpeciesResponse.class)
                .orElseThrow(() -> new InvalidPokemonDataException("pokeapi has no species at " + path));
    }

    private List<EvolutionLink> evolution(PokeApiSpeciesResponse species) {
        if (species.evolutionChain() == null) {
            return List.of();
        }
        String path = "/evolution-chain/" + PokeApiResourceId.of(species.evolutionChain()).value();
        return client.get(path, PokeApiEvolutionChainResponse.class)
                .map(evolutionMapper::flatten)
                .orElseGet(List::of);
    }

    // AC9d — one summary line per page, never 2N
    private void logFanOut(int page, int size, int requested, int returned) {
        log.info(
                "pokeapi page offset={} size={} rows={} dropped={} upstreamCalls={}",
                (long) page * size,
                size,
                returned,
                requested - returned,
                1 + (2L * requested));
    }
}
