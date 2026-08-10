package com.elatusdev.pokedex.pokedex.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.shared.domain.Category;
import com.elatusdev.pokedex.shared.domain.Description;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// F7 / AC5. This is a property over the partition, not a set of examples, for two reasons
// the work unit names:
//
//   - An example whose fixture has EMPTY proprietary fields cannot tell "preserved" from
//     "cleared". All 32 combinations are enumerated, so a merge returning none() fails 31.
//   - An example cannot notice a new field landing in BOTH halves. The structural tests
//     read the record components directly, so the day someone adds `region` to
//     ReplicatedFields the partition stops being disjoint and this goes red.
//
// Both directions are asserted. A test that only checks preservation is satisfied by a
// merge that does nothing at all.
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonMergePolicyTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-08-09T14:22:31Z");
    private final PokemonMergePolicy policy = new PokemonMergePolicy();

    // ---------- the partition itself ----------

    @Test
    void should_declare_a_replicated_partition_that_matches_the_record_exactly() {
        assertThat(componentsOf(ReplicatedFields.class)).isEqualTo(PokemonMergePolicy.REPLICATED_FIELDS);
    }

    @Test
    void should_declare_a_proprietary_partition_that_matches_the_record_exactly() {
        assertThat(componentsOf(ProprietaryFields.class)).isEqualTo(PokemonMergePolicy.PROPRIETARY_FIELDS);
    }

    // the disjointness IS the design: because no field belongs to both authorities, re-sync
    // has nothing to reconcile and the merge is total — ADR-0007
    @Test
    void should_keep_the_two_halves_of_the_partition_disjoint() {
        assertThat(PokemonMergePolicy.REPLICATED_FIELDS)
                .doesNotContainAnyElementsOf(PokemonMergePolicy.PROPRIETARY_FIELDS);
    }

    @Test
    void should_cover_every_field_of_both_records_between_the_two_halves() {
        Set<String> union = new java.util.HashSet<>(PokemonMergePolicy.REPLICATED_FIELDS);
        union.addAll(PokemonMergePolicy.PROPRIETARY_FIELDS);

        Set<String> everyComponent = new java.util.HashSet<>(componentsOf(ReplicatedFields.class));
        everyComponent.addAll(componentsOf(ProprietaryFields.class));

        assertThat(union).isEqualTo(everyComponent);
    }

    // ---------- the behaviour, over every combination of curator data ----------

    @ParameterizedTest(name = "curator data: {0}")
    @MethodSource("everyProprietaryCombination")
    void should_leave_every_proprietary_field_byte_identical(String description, ProprietaryFields curated) {
        Pokemon existing = customised(curated);

        PokemonMergePolicy.MergedFields merged = policy.merge(existing, ivysaurFromUpstream());

        assertThat(merged.proprietary()).isEqualTo(curated);
        assertThat(merged.proprietary().region()).isEqualTo(curated.region());
        assertThat(merged.proprietary().notes()).isEqualTo(curated.notes());
        assertThat(merged.proprietary().curatedBy()).isEqualTo(curated.curatedBy());
        assertThat(merged.proprietary().tags()).isEqualTo(curated.tags());
        assertThat(merged.proprietary().curatorNames()).isEqualTo(curated.curatorNames());
    }

    // the other direction. Without this, a merge that returns the existing record unchanged
    // would satisfy every preservation assertion above.
    @ParameterizedTest(name = "curator data: {0}")
    @MethodSource("everyProprietaryCombination")
    void should_replace_every_replicated_field_with_the_upstream_values(
            String description, ProprietaryFields curated) {
        Pokemon existing = customised(curated);
        ReplicatedFields upstream = ivysaurFromUpstream();
        assertThat(upstream).isNotEqualTo(existing.replicated());

        PokemonMergePolicy.MergedFields merged = policy.merge(existing, upstream);

        assertThat(merged.replicated()).isEqualTo(upstream);
        assertThat(merged.replicated()).isNotEqualTo(existing.replicated());
    }

    @ParameterizedTest(name = "curator data: {0}")
    @MethodSource("everyProprietaryCombination")
    void should_not_mutate_the_record_it_merges_from(String description, ProprietaryFields curated) {
        Pokemon existing = customised(curated);
        ReplicatedFields before = existing.replicated();

        policy.merge(existing, ivysaurFromUpstream());

        assertThat(existing.replicated()).isSameAs(before);
        assertThat(existing.proprietary()).isEqualTo(curated);
    }

    // ---------- generators ----------

    // 2^5 = 32: every subset of the five proprietary fields, present or absent. The empty
    // subset is one case among 32, not the fixture the suite is built on.
    private static Stream<org.junit.jupiter.params.provider.Arguments> everyProprietaryCombination() {
        List<org.junit.jupiter.params.provider.Arguments> cases = new ArrayList<>();
        for (int mask = 0; mask < 32; mask++) {
            cases.add(org.junit.jupiter.params.provider.Arguments.of(describe(mask), proprietaryFor(mask)));
        }
        return cases.stream();
    }

    private static ProprietaryFields proprietaryFor(int mask) {
        return new ProprietaryFields(
                bit(mask, 0) ? Optional.of(Region.KANTO) : Optional.empty(),
                bit(mask, 1) ? Optional.of(new Notes("curator note " + mask)) : Optional.empty(),
                bit(mask, 2) ? Optional.of(UserId.of(7)) : Optional.empty(),
                bit(mask, 3) ? List.of(new Tag("starter"), new Tag("gen-" + mask)) : List.of(),
                bit(mask, 4)
                        ? List.of(new LocalizedName("es", "Bulbasaur " + mask, NameSource.CURATOR))
                        : List.of());
    }

    private static String describe(int mask) {
        List<String> present = new ArrayList<>();
        String[] names = {"region", "notes", "curatedBy", "tags", "curatorNames"};
        for (int bit = 0; bit < names.length; bit++) {
            if (bit(mask, bit)) {
                present.add(names[bit]);
            }
        }
        return present.isEmpty() ? "none" : String.join("+", present);
    }

    private static boolean bit(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    private static Set<String> componentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ---------- fixtures ----------

    private static Pokemon customised(ProprietaryFields curated) {
        return Pokemon.rehydrate(
                PokemonId.of(1),
                Optional.of(PokeApiId.of(1)),
                bulbasaurAsStored(),
                curated,
                ReplicationState.STALE,
                Optional.of(SYNCED_AT),
                3L);
    }

    private static ReplicatedFields bulbasaurAsStored() {
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

    // every replicated component differs from the stored one, so "replaced" is observable
    // on each of them rather than on whichever happens to change
    private static ReplicatedFields ivysaurFromUpstream() {
        return new ReplicatedFields(
                new PokemonName("ivysaur"),
                Optional.of(new Category("Seed Pokemon Evolved")),
                Mass.ofHectograms(130),
                Height.ofDecimetres(10),
                142,
                Sprite.NONE,
                Optional.of(new Description("There is a bud on this Pokemon's back.")),
                List.of(new PokemonAbility("chlorophyll", 3, true)),
                List.of(new PokemonStat("attack", 62, 1)),
                List.of(new PokemonType("poison", 2)),
                List.of(),
                List.of(new LocalizedName("ja", "フシギソウ", NameSource.UPSTREAM)));
    }
}
