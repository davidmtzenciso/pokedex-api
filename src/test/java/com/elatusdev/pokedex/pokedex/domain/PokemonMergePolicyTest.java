package com.elatusdev.pokedex.pokedex.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
import com.elatusdev.pokedex.shared.domain.EvolutionLink;
import com.elatusdev.pokedex.shared.domain.Height;
import com.elatusdev.pokedex.shared.domain.LocalizedName;
import com.elatusdev.pokedex.shared.domain.Mass;
import com.elatusdev.pokedex.shared.domain.NameSource;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonAbility;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import com.elatusdev.pokedex.shared.domain.PokemonStat;
import com.elatusdev.pokedex.shared.domain.PokemonType;
import com.elatusdev.pokedex.shared.domain.ReplicatedFields;
import com.elatusdev.pokedex.shared.domain.Sprite;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// F7 / I5 / AC5, as a property over generated field combinations rather than an example.
//
// The example test this replaces would use a fixture with empty proprietary fields, where
// "preserved" and "cleared" are the same observation — which is precisely the mutant that
// survives PIT and precisely the data-loss bug the policy exists to prevent.
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonMergePolicyTest {

    private static final Instant FIRST_SYNC = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant RE_SYNC = Instant.parse("2026-08-10T18:00:00Z");

    private final PokemonMergePolicy policy = new PokemonMergePolicy();

    // The R11 guard. Every component of both records must be claimed by exactly one side of
    // the partition, checked by reflection — so a field added to either record and named in
    // neither constant fails the build instead of being silently dropped on the next re-sync.
    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class PartitionTotality {

        @Test
        void should_claim_every_replicated_component_when_the_record_is_reflected() {
            assertThat(componentsOf(ReplicatedFields.class))
                    .isNotEmpty()
                    .allSatisfy(component -> assertThat(PokemonMergePolicy.REPLICATED)
                            .describedAs(
                                    "ReplicatedFields.%s belongs to no partition — re-sync would drop it silently",
                                    component)
                            .contains(component));
        }

        @Test
        void should_claim_every_proprietary_component_when_the_record_is_reflected() {
            assertThat(componentsOf(ProprietaryFields.class))
                    .isNotEmpty()
                    .allSatisfy(component -> assertThat(PokemonMergePolicy.PROPRIETARY)
                            .describedAs(
                                    "ProprietaryFields.%s belongs to no partition — re-sync would overwrite it",
                                    component)
                            .contains(component));
        }

        // Proprietary ∩ Replicated = ∅ — the disjointness the whole merge rests on
        @Test
        void should_share_no_field_when_the_two_partitions_are_intersected() {
            assertThat(PokemonMergePolicy.REPLICATED).doesNotContainAnyElementsOf(PokemonMergePolicy.PROPRIETARY);
        }

        @Test
        void should_name_no_field_that_neither_record_declares() {
            Set<String> declared = Stream.concat(
                            componentsOf(ReplicatedFields.class).stream(), componentsOf(ProprietaryFields.class).stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

            assertThat(PokemonMergePolicy.REPLICATED).isSubsetOf(declared);
            assertThat(PokemonMergePolicy.PROPRIETARY).isSubsetOf(declared);
        }

        private List<String> componentsOf(Class<?> record) {
            return Arrays.stream(record.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
        }
    }

    // 32 records, one per subset of the five proprietary fields. The empty subset and the
    // full one are both in here, and so is every partial combination in between.
    @ParameterizedTest(name = "proprietary = {1}")
    @MethodSource("everyProprietaryCombination")
    void should_preserve_every_proprietary_field_when_upstream_replaces_the_replicated_half(
            ProprietaryFields proprietary, String description) {
        Pokemon existing = stale(proprietary);
        ProprietaryFields before = existing.proprietary();

        policy.merge(existing, changedUpstream(), RE_SYNC);

        assertThat(existing.proprietary())
                .describedAs("proprietary fields for %s", description)
                .isEqualTo(before);
    }

    // Without this the suite passes for a merge that does nothing at all — preservation is
    // only half of F7, and it is the half a no-op satisfies.
    @ParameterizedTest(name = "proprietary = {1}")
    @MethodSource("everyProprietaryCombination")
    void should_replace_every_replicated_field_when_upstream_differs(
            ProprietaryFields proprietary, String description) {
        Pokemon existing = stale(proprietary);
        assertThat(existing.replicated()).isNotEqualTo(changedUpstream());

        policy.merge(existing, changedUpstream(), RE_SYNC);

        assertThat(existing.replicated())
                .describedAs("replicated fields for %s", description)
                .isEqualTo(changedUpstream());
    }

    @ParameterizedTest(name = "proprietary = {1}")
    @MethodSource("everyProprietaryCombination")
    void should_land_in_customized_exactly_when_a_proprietary_field_is_present(
            ProprietaryFields proprietary, String description) {
        Pokemon existing = stale(proprietary);

        policy.merge(existing, changedUpstream(), RE_SYNC);

        assertThat(existing.replicationState())
                .describedAs("state for %s", description)
                .isEqualTo(proprietary.isEmpty() ? ReplicationState.SYNCED : ReplicationState.CUSTOMIZED);
    }

    @ParameterizedTest(name = "proprietary = {1}")
    @MethodSource("everyProprietaryCombination")
    void should_stamp_the_resync_time_when_the_merge_completes(
            ProprietaryFields proprietary, String description) {
        Pokemon existing = stale(proprietary);

        policy.merge(existing, changedUpstream(), RE_SYNC);

        assertThat(existing.syncedAt()).describedAs("syncedAt for %s", description).contains(RE_SYNC);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> everyProprietaryCombination() {
        return IntStream.range(0, 1 << 5).mapToObj(PokemonMergePolicyTest::combination);
    }

    private static org.junit.jupiter.params.provider.Arguments combination(int mask) {
        boolean region = (mask & 1) != 0;
        boolean notes = (mask & 2) != 0;
        boolean curator = (mask & 4) != 0;
        boolean tags = (mask & 8) != 0;
        boolean names = (mask & 16) != 0;
        ProprietaryFields fields = new ProprietaryFields(
                region ? Optional.of(Region.KANTO) : Optional.empty(),
                notes ? Optional.of(new Notes("curator wrote this")) : Optional.empty(),
                curator ? Optional.of(UserId.of(7)) : Optional.empty(),
                tags ? List.of(new Tag("starter"), new Tag("kanto")) : List.of(),
                names ? List.of(new LocalizedName("es", "Bulbasaurio", NameSource.CURATOR)) : List.of());
        String label = describe(region, notes, curator, tags, names);
        return org.junit.jupiter.params.provider.Arguments.of(fields, label);
    }

    private static String describe(boolean region, boolean notes, boolean curator, boolean tags, boolean names) {
        StringBuilder label = new StringBuilder();
        if (region) {
            label.append("region ");
        }
        if (notes) {
            label.append("notes ");
        }
        if (curator) {
            label.append("curatedBy ");
        }
        if (tags) {
            label.append("tags ");
        }
        if (names) {
            label.append("curatorNames ");
        }
        return label.isEmpty() ? "none" : label.toString().strip();
    }

    private static Pokemon stale(ProprietaryFields proprietary) {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(PokeApiId.of(1)),
                originalUpstream(),
                proprietary,
                ReplicationState.STALE,
                Optional.of(FIRST_SYNC),
                3L);
    }

    private static ReplicatedFields originalUpstream() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokémon")),
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                new Sprite(URI.create("https://img.example/1.png"), URI.create("https://img.example/1-art.png")),
                Optional.of(new Description("A strange seed was planted on its back at birth.")),
                List.of(new PokemonAbility("overgrow", 1, false)),
                List.of(new PokemonStat("hp", 45, 0)),
                List.of(new PokemonType("grass", 1)),
                List.of(new EvolutionLink(PokeApiId.of(1), PokeApiId.of(2), "level-up", Optional.of(16))),
                List.of(new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM)));
    }

    // every component differs from originalUpstream, so no field can pass by accident
    private static ReplicatedFields changedUpstream() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur-redux"),
                Optional.of(new Category("Renamed Pokémon")),
                Mass.ofHectograms(70),
                Height.ofDecimetres(8),
                65,
                new Sprite(URI.create("https://img.example/1-v2.png"), URI.create("https://img.example/1-art-v2.png")),
                Optional.of(new Description("Upstream rewrote this entry.")),
                List.of(new PokemonAbility("chlorophyll", 3, true)),
                List.of(new PokemonStat("attack", 49, 1)),
                List.of(new PokemonType("poison", 2)),
                List.of(new EvolutionLink(PokeApiId.of(1), PokeApiId.of(3), "trade", Optional.empty())),
                List.of(new LocalizedName("de", "Bisasam", NameSource.UPSTREAM)));
    }
}
