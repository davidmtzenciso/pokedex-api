package com.elatusdev.pokedex.pokedex.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalPokemonFilterTest {

    @Test
    void should_match_everything_when_no_filter_is_set() {
        LocalPokemonFilter filter = LocalPokemonFilter.none();

        assertThat(filter.isEmpty()).isTrue();
        assertThat(filter.region()).isEmpty();
        assertThat(filter.tag()).isEmpty();
        assertThat(filter.nameContains()).isEmpty();
    }

    @Test
    void should_not_be_empty_when_a_region_is_set() {
        assertThat(new LocalPokemonFilter(Optional.of(Region.KANTO), Optional.empty(), Optional.empty()).isEmpty())
                .isFalse();
    }

    @Test
    void should_not_be_empty_when_a_tag_is_set() {
        assertThat(new LocalPokemonFilter(Optional.empty(), Optional.of(new Tag("starter")), Optional.empty())
                        .isEmpty())
                .isFalse();
    }

    @Test
    void should_not_be_empty_when_a_name_fragment_is_set() {
        assertThat(new LocalPokemonFilter(Optional.empty(), Optional.empty(), Optional.of("chu")).isEmpty())
                .isFalse();
    }

    // a query string of spaces is a filter the caller did not mean to set; treating it as
    // one would return nothing and look like "no such Pokemon"
    @Test
    void should_treat_a_blank_name_fragment_as_no_filter_at_all() {
        LocalPokemonFilter filter = new LocalPokemonFilter(Optional.empty(), Optional.empty(), Optional.of("   "));

        assertThat(filter.nameContains()).isEmpty();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void should_strip_surrounding_whitespace_from_a_name_fragment() {
        LocalPokemonFilter filter = new LocalPokemonFilter(Optional.empty(), Optional.empty(), Optional.of("  chu  "));

        assertThat(filter.nameContains()).contains("chu");
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void should_reject_a_null_region() {
        assertThatThrownBy(() -> new LocalPokemonFilter(null, Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("region");
    }

    @Test
    void should_reject_a_null_tag() {
        assertThatThrownBy(() -> new LocalPokemonFilter(Optional.empty(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tag");
    }

    @Test
    void should_reject_a_null_name_fragment() {
        assertThatThrownBy(() -> new LocalPokemonFilter(Optional.empty(), Optional.empty(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("nameContains");
    }
}
