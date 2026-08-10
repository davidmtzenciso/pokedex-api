package com.elatusdev.pokedex.pokedex.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.ProprietaryFields;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.identity.domain.UserRepository;
import com.elatusdev.pokedex.pokedex.domain.Notes;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.pokedex.domain.Region;
import com.elatusdev.pokedex.pokedex.domain.Tag;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.elatusdev.pokedex.testsupport.PokemonFixture;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonRepositoryAdapterComponentTest {

    private final PokemonRepository repository;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    PokemonRepositoryAdapterComponentTest(
            @Autowired PokemonRepository repository, @Autowired UserRepository users, @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.users = users;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    @Test
    void should_return_every_replicated_field_unchanged_when_a_saved_pokemon_is_read_back() {
        Pokemon saved = repository.save(PokemonFixture.syncedBulbasaur());

        Pokemon found = repository.findById(saved.id().orElseThrow()).orElseThrow();

        assertThat(found.replicated()).isEqualTo(PokemonFixture.bulbasaur());
        assertThat(found.pokeApiId()).contains(PokeApiId.of(1));
        assertThat(found.replicationState()).isEqualTo(ReplicationState.SYNCED);
        assertThat(found.syncedAt()).contains(PokemonFixture.SYNCED_AT);
    }

    @Test
    void should_return_every_proprietary_field_unchanged_when_a_curated_pokemon_is_read_back() {
        UserId curator = users.save(PokemonFixture.curator("ash")).id().orElseThrow();
        Pokemon pokemon = PokemonFixture.syncedBulbasaur();
        pokemon.assignRegion(Region.KANTO);
        pokemon.annotate(new Notes("starter of the Kanto trio"));
        pokemon.addTag(new Tag("starter"));
        pokemon.curateBy(curator);
        Pokemon saved = repository.save(pokemon);

        Pokemon found = repository.findById(saved.id().orElseThrow()).orElseThrow();

        assertThat(found.proprietary())
                .isEqualTo(new ProprietaryFields(
                        Optional.of(Region.KANTO),
                        Optional.of(new Notes("starter of the Kanto trio")),
                        Optional.of(curator),
                        List.of(new Tag("starter")),
                        List.of()));
    }

    // localized_name is the one table both halves of the partition share, so a mapper that
    // read the discriminator wrongly would hand curator names back as upstream ones and the
    // next re-sync would delete them
    @Test
    void should_split_localized_names_by_source_when_both_halves_are_present() {
        Pokemon pokemon = PokemonFixture.syncedBulbasaur();
        pokemon.assignRegion(Region.JOHTO);
        Pokemon saved = repository.save(withCuratorName(repository.save(pokemon)));

        Pokemon found = repository.findById(saved.id().orElseThrow()).orElseThrow();

        assertThat(found.replicated().upstreamNames())
                .containsExactly(
                        new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM),
                        new LocalizedName("fr", "Bulbizarre", NameSource.UPSTREAM));
        assertThat(found.proprietary().curatorNames())
                .containsExactly(new LocalizedName("es", "Bulbasaurio de Ash", NameSource.CURATOR));
    }

    // the persistence half of F7/AC5: the store must be able to replace every replicated
    // field without disturbing a single proprietary one. The policy that decides to do so is
    // WU-US03-B; this is the guarantee it relies on.
    @Test
    void should_replace_replicated_and_preserve_proprietary_when_a_customised_record_is_resynced() {
        UserId curator = users.save(PokemonFixture.curator("misty")).id().orElseThrow();
        Pokemon pokemon = PokemonFixture.syncedBulbasaur();
        pokemon.assignRegion(Region.KANTO);
        pokemon.annotate(new Notes("do not overwrite"));
        pokemon.addTag(new Tag("starter"));
        pokemon.curateBy(curator);
        Pokemon customised = repository.save(withCuratorName(repository.save(pokemon)));
        ProprietaryFields before = customised.proprietary();

        customised.transitionTo(ReplicationState.STALE, PokemonFixture.SYNCED_AT);
        customised.replaceReplicated(PokemonFixture.changedUpstream(), PokemonFixture.SYNCED_AT);
        PokemonId id = repository.save(customised).id().orElseThrow();

        Pokemon found = repository.findById(id).orElseThrow();
        assertThat(found.replicated()).isEqualTo(PokemonFixture.changedUpstream());
        assertThat(found.proprietary()).isEqualTo(before);
        assertThat(found.replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
    }

    @Test
    void should_leave_no_replaced_child_row_behind_when_replicated_children_are_replaced() {
        Pokemon saved = repository.save(PokemonFixture.syncedBulbasaur());
        saved.transitionTo(ReplicationState.STALE, PokemonFixture.SYNCED_AT);
        saved.replaceReplicated(PokemonFixture.changedUpstream(), PokemonFixture.SYNCED_AT);

        repository.save(saved);

        assertThat(rowCount("pokemon_ability")).isEqualTo(1);
        assertThat(rowCount("pokemon_stat")).isEqualTo(1);
        assertThat(rowCount("pokemon_type")).isEqualTo(1);
        assertThat(rowCount("evolution_link")).isEqualTo(1);
        assertThat(rowCount("localized_name")).isEqualTo(1);
    }

    @Test
    void should_assign_an_id_and_start_at_version_zero_when_a_new_pokemon_is_saved() {
        Pokemon saved = repository.save(PokemonFixture.syncedBulbasaur());

        assertThat(saved.id()).contains(PokemonId.of(1));
        assertThat(saved.version()).isZero();
    }

    @Test
    void should_increment_the_version_when_an_existing_pokemon_is_saved_again() {
        Pokemon saved = repository.save(PokemonFixture.syncedBulbasaur());
        saved.assignRegion(Region.KANTO);

        assertThat(repository.save(saved).version()).isEqualTo(1L);
    }

    @Test
    void should_find_by_poke_api_id_when_the_record_is_linked_to_upstream() {
        PokemonId id = repository.save(PokemonFixture.syncedBulbasaur()).id().orElseThrow();

        assertThat(repository.findByPokeApiId(PokeApiId.of(1)).flatMap(Pokemon::id)).contains(id);
    }

    @Test
    void should_find_by_name_when_the_case_differs() {
        PokemonId id = repository.save(PokemonFixture.syncedBulbasaur()).id().orElseThrow();

        assertThat(repository.findByName(new PokemonName("BULBASAUR")).flatMap(Pokemon::id))
                .contains(id);
    }

    // two DRAFT rows may share a name, and answering that with an exception would turn a
    // legitimate state into a 500
    @Test
    void should_return_the_first_match_when_two_drafts_share_a_name() {
        PokemonId first = repository.save(PokemonFixture.draft("pikachu")).id().orElseThrow();
        repository.save(PokemonFixture.draft("pikachu"));

        assertThat(repository.findByName(new PokemonName("pikachu")).flatMap(Pokemon::id))
                .contains(first);
    }

    @Test
    void should_return_empty_when_nothing_matches_the_lookup() {
        assertThat(repository.findById(PokemonId.of(404))).isEmpty();
        assertThat(repository.findByPokeApiId(PokeApiId.of(404))).isEmpty();
        assertThat(repository.findByName(new PokemonName("missingno"))).isEmpty();
        assertThat(repository.existsByPokeApiId(PokeApiId.of(404))).isFalse();
    }

    @Test
    void should_report_the_record_exists_when_the_poke_api_id_is_replicated() {
        repository.save(PokemonFixture.syncedBulbasaur());

        assertThat(repository.existsByPokeApiId(PokeApiId.of(1))).isTrue();
    }

    @Test
    void should_page_in_id_order_when_more_rows_exist_than_the_page_size() {
        for (int pokeApiId = 1; pokeApiId <= 5; pokeApiId++) {
            repository.save(PokemonFixture.synced(pokeApiId, PokemonFixture.bulbasaur()));
        }

        assertThat(repository.findPage(0, 2).stream().map(Pokemon::pokeApiId).toList())
                .containsExactly(Optional.of(PokeApiId.of(1)), Optional.of(PokeApiId.of(2)));
        assertThat(repository.findPage(2, 2).stream().map(Pokemon::pokeApiId).toList())
                .containsExactly(Optional.of(PokeApiId.of(5)));
        assertThat(repository.count()).isEqualTo(5L);
    }

    @Test
    void should_return_an_empty_page_when_the_offset_is_past_the_end() {
        repository.save(PokemonFixture.syncedBulbasaur());

        assertThat(repository.findPage(3, 10)).isEmpty();
    }

    // ProprietaryFields has no withCuratorNames, because nothing in WU-US03-A writes one —
    // the curator-facing path arrives with WU-US04. Rehydrating a persisted record is how a
    // test reaches the half of the partition the domain cannot yet populate.
    private Pokemon withCuratorName(Pokemon pokemon) {
        ProprietaryFields proprietary = pokemon.proprietary();
        return Pokemon.rehydrate(
                pokemon.id().orElseThrow(),
                pokemon.pokeApiId(),
                pokemon.replicated(),
                new ProprietaryFields(
                        proprietary.region(),
                        proprietary.notes(),
                        proprietary.curatedBy(),
                        proprietary.tags(),
                        List.of(new LocalizedName("es", "Bulbasaurio de Ash", NameSource.CURATOR))),
                pokemon.replicationState(),
                pokemon.syncedAt(),
                pokemon.version());
    }

    private int rowCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
