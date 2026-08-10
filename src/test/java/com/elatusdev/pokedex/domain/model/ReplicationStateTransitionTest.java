// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import static com.elatusdev.pokedex.domain.model.ReplicationState.CUSTOMIZED;
import static com.elatusdev.pokedex.domain.model.ReplicationState.DRAFT;
import static com.elatusdev.pokedex.domain.model.ReplicationState.FAILED;
import static com.elatusdev.pokedex.domain.model.ReplicationState.PENDING;
import static com.elatusdev.pokedex.domain.model.ReplicationState.STALE;
import static com.elatusdev.pokedex.domain.model.ReplicationState.SYNCED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.IllegalStateTransitionException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReplicationStateTransitionTest {

    private static Set<Arguments> legalEdges() {
        return Set.of(
                Arguments.of(DRAFT, PENDING), Arguments.of(DRAFT, DRAFT),
                Arguments.of(PENDING, SYNCED), Arguments.of(PENDING, FAILED),
                Arguments.of(SYNCED, CUSTOMIZED), Arguments.of(SYNCED, STALE),
                Arguments.of(CUSTOMIZED, CUSTOMIZED), Arguments.of(CUSTOMIZED, STALE),
                Arguments.of(STALE, SYNCED), Arguments.of(STALE, CUSTOMIZED), Arguments.of(STALE, FAILED),
                Arguments.of(FAILED, PENDING));
    }

    private static Set<Arguments> illegalEdges() {
        var legal = legalEdges().stream()
                .map(a -> a.get()[0] + "->" + a.get()[1])
                .collect(java.util.stream.Collectors.toSet());
        var all = new java.util.HashSet<Arguments>();
        for (var from : ReplicationState.values()) {
            for (var to : ReplicationState.values()) {
                if (!legal.contains(from + "->" + to)) {
                    all.add(Arguments.of(from, to));
                }
            }
        }
        return all;
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @MethodSource("legalEdges")
    void should_allow_every_edge_on_the_diagram(ReplicationState from, ReplicationState to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        assertThat(from.transitionTo(to)).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("illegalEdges")
    void should_reject_every_edge_not_on_the_diagram(ReplicationState from, ReplicationState to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.transitionTo(to))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void should_report_which_states_hold_replicated_data() {
        assertThat(EnumSet.allOf(ReplicationState.class).stream().filter(ReplicationState::isReplicated))
                .containsExactlyInAnyOrder(SYNCED, CUSTOMIZED, STALE);
    }

    @Test
    void should_expose_the_failure_context_on_an_illegal_transition() {
        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateTransitionException.class, () -> SYNCED.transitionTo(PENDING));
        assertThat(ex.from()).isEqualTo(SYNCED);
        assertThat(ex.to()).isEqualTo(PENDING);
    }
}
