package com.elatusdev.pokedex.pokedex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.pokedex.domain.BatchRangeTooLargeException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.UpstreamReplicationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// AC-US03-4. Partial success is the expected outcome against a public API, so it is modelled
// rather than treated as an error: the three counts partition the requested range exactly,
// and failedIds makes a re-run target only the remainder.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BatchSyncUseCaseTest {

    @Mock
    private SyncPokemonUseCase sync;

    private BatchSyncUseCase useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new BatchSyncUseCase(sync);
    }

    @Test
    void should_count_every_id_as_succeeded_when_all_of_them_replicate() {
        stubSucceeding(1, 5);

        BatchSyncSummary summary = useCase.sync(1, 5);

        assertThat(summary.succeeded()).isEqualTo(5);
        assertThat(summary.failed()).isZero();
        assertThat(summary.skipped()).isZero();
        assertThat(summary.failedIds()).isEmpty();
    }

    // the property that matters: whatever happens per id, nothing is lost or double-counted
    @Test
    void should_partition_the_requested_range_when_outcomes_are_mixed() {
        when(sync.sync("1")).thenReturn(new SyncResult(synced(), true));
        when(sync.sync("2")).thenThrow(new PokemonNotFoundException("2"));
        when(sync.sync("3"))
                .thenThrow(new IllegalStateTransitionException(ReplicationState.SYNCED, ReplicationState.STALE));
        when(sync.sync("4"))
                .thenThrow(new UpstreamReplicationFailedException("4", new IllegalStateException("upstream down")));
        when(sync.sync("5")).thenReturn(new SyncResult(synced(), false));

        BatchSyncSummary summary = useCase.sync(1, 5);

        assertThat(summary.succeeded() + summary.failed() + summary.skipped()).isEqualTo(5);
        assertThat(summary.succeeded()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
    }

    // a record that is simply up to date is not a failure — re-running the batch would do
    // nothing for it, so listing it in failedIds would send the caller after a non-problem
    @Test
    void should_count_an_up_to_date_record_as_skipped_rather_than_failed() {
        when(sync.sync("1"))
                .thenThrow(new IllegalStateTransitionException(ReplicationState.CUSTOMIZED, ReplicationState.STALE));

        BatchSyncSummary summary = useCase.sync(1, 1);

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(summary.failedIds()).isEmpty();
    }

    @Test
    void should_list_only_the_failures_when_a_rerun_should_target_the_remainder() {
        when(sync.sync("1")).thenReturn(new SyncResult(synced(), true));
        when(sync.sync("2")).thenThrow(new PokemonNotFoundException("2"));
        when(sync.sync("3"))
                .thenThrow(new UpstreamReplicationFailedException("3", new IllegalStateException("upstream down")));

        assertThat(useCase.sync(1, 3).failedIds()).containsExactly(2, 3);
    }

    @Test
    void should_reject_the_batch_when_the_range_exceeds_the_cap() {
        assertThatThrownBy(() -> useCase.sync(1, BatchSyncUseCase.MAX_BATCH + 1))
                .isInstanceOf(BatchRangeTooLargeException.class)
                .hasMessageContaining(String.valueOf(BatchSyncUseCase.MAX_BATCH));

        verifyNoInteractions(sync);
    }

    @Test
    void should_accept_a_batch_of_exactly_the_cap() {
        stubSucceeding(1, BatchSyncUseCase.MAX_BATCH);

        assertThat(useCase.sync(1, BatchSyncUseCase.MAX_BATCH).succeeded()).isEqualTo(BatchSyncUseCase.MAX_BATCH);
    }

    @Test
    void should_reject_the_batch_when_the_range_runs_backwards() {
        assertThatThrownBy(() -> useCase.sync(10, 1)).isInstanceOf(BatchRangeTooLargeException.class);

        verifyNoInteractions(sync);
    }

    @Test
    void should_sync_each_id_exactly_once_when_the_range_is_fanned_out() {
        stubSucceeding(1, 3);

        useCase.sync(1, 3);

        verify(sync, times(1)).sync("1");
        verify(sync, times(1)).sync("2");
        verify(sync, times(1)).sync("3");
    }

    private void stubSucceeding(int from, int to) {
        for (int id = from; id <= to; id++) {
            when(sync.sync(String.valueOf(id))).thenReturn(new SyncResult(synced(), true));
        }
    }

    private static Pokemon synced() {
        return SyncFixture.synced();
    }
}
