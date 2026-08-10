package com.elatusdev.pokedex.pokedex.application;

import java.util.List;
import java.util.Objects;

// succeeded + failed + skipped = the requested range, always. The three sets partition it,
// which is what makes the 202 body honest: a caller can tell from the numbers alone that
// nothing was silently dropped.
public record BatchSyncSummary(int succeeded, int failed, int skipped, List<Integer> failedIds) {

    public BatchSyncSummary {
        failedIds = List.copyOf(Objects.requireNonNull(failedIds, "failedIds"));
    }

    public int total() {
        return succeeded + failed + skipped;
    }
}
