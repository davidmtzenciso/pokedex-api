package com.elatusdev.pokedex.pokedex.domain;

import java.util.List;

// The filtered read side of the local catalogue, kept apart from PokemonRepository because
// the two answer different questions: the repository loads an aggregate to change it, this
// answers a query. Splitting them also keeps the sync work unit and this one out of each
// other's files.
//
// count() takes the same filter as findPage() on purpose — AC-US04-6 requires the page
// metadata to report the FILTERED total, and a count that ignores the filter is the
// classic way to get a last page that does not exist.
public interface LocalPokemonQuery {

    List<Pokemon> findPage(LocalPokemonFilter filter, int page, int size);

    long count(LocalPokemonFilter filter);
}
