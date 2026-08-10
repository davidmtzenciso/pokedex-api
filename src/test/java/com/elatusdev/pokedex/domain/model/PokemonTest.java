// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.IllegalStateTransitionException;
import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.Category;
import com.elatusdev.pokedex.domain.vo.Description;
import com.elatusdev.pokedex.domain.vo.Height;
import com.elatusdev.pokedex.domain.vo.Mass;
import com.elatusdev.pokedex.domain.vo.Notes;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import com.elatusdev.pokedex.domain.vo.Region;
import com.elatusdev.pokedex.domain.vo.Sprite;
import com.elatusdev.pokedex.domain.vo.Tag;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PokemonTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-08-09T14:22:31Z");

    private static ReplicatedFields bulbasaur() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokemon")),
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                Sprite.NONE,
                Optional.of(new Description("A strange seed was planted on its back at birth.")),
                List.of(new PokemonAbility("overgrow", 1, false)),
                List.of(new PokemonStat("speed", 45, 0)),
                List.of(new PokemonType("grass", 1)),
                List.of(),
                List.of(new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM)));
    }

    private static Pokemon synced() {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(PokeApiId.of(1)),
                bulbasaur(),
                ProprietaryFields.none(),
                ReplicationState.SYNCED,
                Optional.of(SYNCED_AT),
                0L);
    }

    @Nested
    class PokemonTagLimitTest {

        @Test
        void should_accept_ten_tags() {
            Pokemon pokemon = synced();

            for (int i = 1; i <= 10; i++) {
                pokemon.addTag(new Tag("tag-" + i));
            }

            assertThat(pokemon.tags()).hasSize(10);
        }

        @Test
        void should_reject_an_eleventh_tag() {
            Pokemon pokemon = synced();
            for (int i = 1; i <= 10; i++) {
                pokemon.addTag(new Tag("tag-" + i));
            }

            assertThatThrownBy(() -> pokemon.addTag(new Tag("tag-11")))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("at most 10 tags");

            assertThat(pokemon.tags()).hasSize(10);
        }

        @Test
        void should_reject_a_tag_that_differs_only_by_case() {
            Pokemon pokemon = synced();
            pokemon.addTag(new Tag("Starter"));

            assertThatThrownBy(() -> pokemon.addTag(new Tag("STARTER")))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("already carries");

            assertThat(pokemon.tags()).containsExactly(new Tag("Starter"));
        }

        @Test
        void should_remove_a_tag_case_insensitively() {
            Pokemon pokemon = synced();
            pokemon.addTag(new Tag("Starter"));

            pokemon.removeTag(new Tag("starter"));

            assertThat(pokemon.tags()).isEmpty();
        }
    }

    @Nested
    class ReplicationInvariantTest {

        @Test
        void should_be_draft_and_carry_no_upstream_id_when_created_locally() {
            Pokemon draft = Pokemon.draft(bulbasaur());

            assertThat(draft.replicationState()).isEqualTo(ReplicationState.DRAFT);
            assertThat(draft.pokeApiId()).isEmpty();
            assertThat(draft.syncedAt()).isEmpty();
        }

        @Test
        void should_reject_a_draft_that_carries_an_upstream_id() {
            assertThatThrownBy(() -> Pokemon.rehydrate(
                            PokemonId.of(1),
                            Optional.of(PokeApiId.of(1)),
                            bulbasaur(),
                            ProprietaryFields.none(),
                            ReplicationState.DRAFT,
                            Optional.empty(),
                            0L))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        void should_reject_a_non_draft_that_carries_no_upstream_id() {
            assertThatThrownBy(() -> Pokemon.rehydrate(
                            PokemonId.of(1),
                            Optional.empty(),
                            bulbasaur(),
                            ProprietaryFields.none(),
                            ReplicationState.PENDING,
                            Optional.empty(),
                            0L))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        void should_reject_a_replicated_state_without_a_sync_timestamp() {
            assertThatThrownBy(() -> Pokemon.rehydrate(
                            PokemonId.of(1),
                            Optional.of(PokeApiId.of(1)),
                            bulbasaur(),
                            ProprietaryFields.none(),
                            ReplicationState.SYNCED,
                            Optional.empty(),
                            0L))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("syncedAt");
        }

        @Test
        void should_carry_a_sync_timestamp_when_the_state_is_replicated() {
            assertThat(synced().syncedAt()).contains(SYNCED_AT);
        }

        @Test
        void should_move_to_the_next_state_when_the_edge_is_on_the_diagram() {
            Pokemon pokemon = synced();

            pokemon.transitionTo(ReplicationState.STALE, SYNCED_AT);

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.STALE);
        }

        @Test
        void should_reject_a_transition_that_is_not_on_the_diagram() {
            Pokemon pokemon = synced();

            assertThatThrownBy(() -> pokemon.transitionTo(ReplicationState.PENDING, SYNCED_AT))
                    .isInstanceOf(IllegalStateTransitionException.class);

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.SYNCED);
        }

        @Test
        void should_leave_the_sync_timestamp_alone_when_the_target_state_is_not_replicated() {
            Pokemon pokemon = Pokemon.pending(PokeApiId.of(1), bulbasaur());

            pokemon.transitionTo(ReplicationState.FAILED, SYNCED_AT);

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.FAILED);
            assertThat(pokemon.syncedAt()).isEmpty();
        }

        @Test
        void should_link_a_draft_to_upstream_when_moving_off_draft() {
            Pokemon draft = Pokemon.draft(bulbasaur());

            draft.linkToUpstream(PokeApiId.of(1));

            assertThat(draft.replicationState()).isEqualTo(ReplicationState.PENDING);
            assertThat(draft.pokeApiId()).contains(PokeApiId.of(1));
        }
    }

    // The single edge on which upstream data reaches a persisted row: STALE -> {SYNCED,
    // CUSTOMIZED}, chosen by whether the curator has written anything. The exhaustive
    // property over field combinations is PokemonMergePolicyTest in WU-US03-B.
    @Nested
    class ReplicationMergeTest {

        private static Pokemon stale() {
            Pokemon pokemon = synced();
            pokemon.transitionTo(ReplicationState.STALE, SYNCED_AT);
            return pokemon;
        }

        private static ReplicatedFields ivysaur() {
            return new ReplicatedFields(
                    new PokemonName("ivysaur"),
                    Optional.of(new Category("Seed Pokemon")),
                    Mass.ofHectograms(130),
                    Height.ofDecimetres(10),
                    142,
                    Sprite.NONE,
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        @Test
        void should_enter_pending_when_a_sync_is_requested_for_an_upstream_id() {
            Pokemon pokemon = Pokemon.pending(PokeApiId.of(4), bulbasaur());

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.PENDING);
            assertThat(pokemon.pokeApiId()).contains(PokeApiId.of(4));
            assertThat(pokemon.id()).isEmpty();
            assertThat(pokemon.version()).isZero();
        }

        @Test
        void should_become_synced_when_the_curator_has_written_nothing() {
            Pokemon pokemon = stale();
            Instant resyncedAt = SYNCED_AT.plusSeconds(86_400);

            pokemon.replaceReplicated(ivysaur(), resyncedAt);

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.SYNCED);
            assertThat(pokemon.replicated().name()).isEqualTo(new PokemonName("ivysaur"));
            assertThat(pokemon.syncedAt()).contains(resyncedAt);
        }

        @Test
        void should_become_customized_when_the_curator_has_written_something() {
            Pokemon pokemon = stale();
            pokemon.assignRegion(Region.KANTO);

            pokemon.replaceReplicated(ivysaur(), SYNCED_AT.plusSeconds(86_400));

            assertThat(pokemon.replicationState()).isEqualTo(ReplicationState.CUSTOMIZED);
        }

        @Test
        void should_leave_every_proprietary_field_untouched_when_replicated_data_is_replaced() {
            Pokemon pokemon = stale();
            pokemon.assignRegion(Region.KANTO);
            pokemon.annotate(new Notes("verify the sprite"));
            pokemon.curateBy(UserId.of(7));
            pokemon.addTag(new Tag("starter"));
            ProprietaryFields before = pokemon.proprietary();

            pokemon.replaceReplicated(ivysaur(), SYNCED_AT.plusSeconds(86_400));

            assertThat(pokemon.proprietary()).isEqualTo(before);
            assertThat(pokemon.proprietary().region()).contains(Region.KANTO);
            assertThat(pokemon.proprietary().notes()).contains(new Notes("verify the sprite"));
            assertThat(pokemon.curatedBy()).contains(UserId.of(7));
            assertThat(pokemon.tags()).containsExactly(new Tag("starter"));
        }

        @Test
        void should_reject_replacing_replicated_data_from_a_state_that_is_not_stale() {
            Pokemon pokemon = synced();

            assertThatThrownBy(() -> pokemon.replaceReplicated(ivysaur(), SYNCED_AT))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    class AggregateEncapsulationTest {

        @Test
        void should_reference_the_curator_by_id_only() {
            Pokemon pokemon = synced();

            pokemon.curateBy(UserId.of(7));

            assertThat(pokemon.curatedBy()).contains(UserId.of(7));
        }

        @Test
        void should_return_an_unmodifiable_view_of_the_tags() {
            List<Tag> tags = synced().tags();

            assertThatThrownBy(() -> tags.add(new Tag("injected")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_return_an_unmodifiable_view_of_the_abilities() {
            List<PokemonAbility> abilities = synced().replicated().abilities();

            assertThatThrownBy(() -> abilities.add(new PokemonAbility("chlorophyll", 3, true)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_not_be_affected_when_the_caller_mutates_the_list_it_passed_in() {
            List<PokemonAbility> mutable = new ArrayList<>(List.of(new PokemonAbility("overgrow", 1, false)));
            ReplicatedFields fields = new ReplicatedFields(
                    new PokemonName("bulbasaur"),
                    Optional.empty(),
                    Mass.ofHectograms(69),
                    Height.ofDecimetres(7),
                    64,
                    Sprite.NONE,
                    Optional.empty(),
                    mutable,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());

            mutable.add(new PokemonAbility("chlorophyll", 3, true));

            assertThat(fields.abilities()).hasSize(1);
        }
    }

    @Nested
    class ProprietaryFieldsTest {

        @Test
        void should_be_empty_when_the_curator_has_written_nothing() {
            assertThat(ProprietaryFields.none().isEmpty()).isTrue();
        }

        @Test
        void should_not_be_empty_when_a_region_is_set() {
            assertThat(synced().proprietary().withRegion(Region.KANTO).isEmpty()).isFalse();
        }

        @Test
        void should_not_be_empty_when_notes_are_set() {
            assertThat(ProprietaryFields.none().withNotes(new Notes("check the sprite")).isEmpty())
                    .isFalse();
        }

        @Test
        void should_not_be_empty_when_a_tag_is_present() {
            Pokemon pokemon = synced();
            pokemon.addTag(new Tag("starter"));

            assertThat(pokemon.proprietary().isEmpty()).isFalse();
        }

        @Test
        void should_not_be_empty_when_a_curator_is_assigned() {
            assertThat(ProprietaryFields.none().withCurator(UserId.of(7)).isEmpty()).isFalse();
        }

        @Test
        void should_not_be_empty_when_a_curator_authored_name_is_present() {
            ProprietaryFields fields = new ProprietaryFields(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    List.of(new LocalizedName("es", "Bulbasaur", NameSource.CURATOR)));

            assertThat(fields.isEmpty()).isFalse();
        }

        @Test
        void should_reject_an_upstream_sourced_name_among_the_curator_names() {
            assertThatThrownBy(() -> new ProprietaryFields(
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            List.of(),
                            List.of(new LocalizedName("es", "Bulbasaur", NameSource.UPSTREAM))))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("CURATOR");
        }
    }

    @Nested
    class ReplicatedFieldsTest {

        @Test
        void should_reject_a_curator_sourced_name_among_the_upstream_names() {
            assertThatThrownBy(() -> new ReplicatedFields(
                            new PokemonName("bulbasaur"),
                            Optional.empty(),
                            Mass.ofHectograms(69),
                            Height.ofDecimetres(7),
                            64,
                            Sprite.NONE,
                            Optional.empty(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(new LocalizedName("es", "Bulbasaur", NameSource.CURATOR))))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("UPSTREAM");
        }

        @Test
        void should_reject_a_negative_base_experience() {
            assertThatThrownBy(() -> new ReplicatedFields(
                            new PokemonName("bulbasaur"),
                            Optional.empty(),
                            Mass.ofHectograms(69),
                            Height.ofDecimetres(7),
                            -1,
                            Sprite.NONE,
                            Optional.empty(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()))
                    .isInstanceOf(InvalidPokemonDataException.class)
                    .hasMessageContaining("baseExperience");
        }
    }
}
