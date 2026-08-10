package com.elatusdev.pokedex.pokedex.domain;

// A batch is capped because PokeAPI is fair-use rate-limited (IA10): a naive from=1,to=1025
// issues roughly 3,000 upstream requests and earns a 429 for everything after it.
//
// Rejected, never truncated — the same reasoning as the page-size cap. Silently syncing the
// first 200 of 1000 leaves the caller believing all 1000 are replicated, and the 800 that
// are not will be discovered by whoever reads them next.
public class BatchRangeTooLargeException extends RuntimeException {

    private final transient int requested;
    private final transient int maximum;

    public BatchRangeTooLargeException(int requested, int maximum) {
        super("A batch covers at most " + maximum + " ids, was " + requested);
        this.requested = requested;
        this.maximum = maximum;
    }

    public int requested() {
        return requested;
    }

    public int maximum() {
        return maximum;
    }
}
