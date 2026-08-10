package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonQuery;
import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListLocalPokemonUseCase {

    private static final int MAX_SIZE = 100;

    private final LocalPokemonQuery query;

    public ListLocalPokemonUseCase(LocalPokemonQuery query) {
        this.query = query;
    }

    public LocalPokemonPageResult list(LocalPokemonFilter filter, int page, int size) {
        requireValidPageRequest(page, size);
        // the count takes the same filter as the page: a total over the whole table would
        // advertise pages the filter cannot fill (AC-US04-6)
        return new LocalPokemonPageResult(query.findPage(filter, page, size), page, size, query.count(filter));
    }

    // rejected, never clamped — a clamped page lets a caller believe it read everything
    private static void requireValidPageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidPaginationException(
                    "page must be >= 0 and size must be between 1 and " + MAX_SIZE, page, size);
        }
    }
}
