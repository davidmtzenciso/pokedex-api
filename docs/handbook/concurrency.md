# Concurrency

We have exactly one concurrency problem: the `1 + 2N` fan-out per page. This document is
about solving that one well and not inventing others.

---

## The problem

PokeAPI's list endpoint returns only `{name, url}`. Sprite, mass, and abilities need a
per-row detail call; the category needs a per-row species call. A page of N rows costs
**1 + 2N upstream requests** — 21 at the default page size of 10, and 201 at the maximum of
100. Sequentially at ~80 ms each, that is 1.7 s and 16 s respectively.

## The solution: bounded virtual-thread fan-out

```java
private static final int MAX_CONCURRENCY = 16;

public List<PokemonSummary> fetchPage(int offset, int limit) {
    var refs = listEndpoint.fetch(offset, limit);          // 1 call
    var semaphore = new Semaphore(MAX_CONCURRENCY);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures = refs.stream()
            .map(ref -> CompletableFuture.supplyAsync(() -> {
                semaphore.acquireUninterruptibly();
                try { return fetchOne(ref); }
                finally { semaphore.release(); }
            }, executor))
            .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }
}
```

Three decisions, each load-bearing:

**Virtual threads, not a platform pool.** 40 blocking HTTP calls are essentially free on
virtual threads, and the stack trace stays readable. No pool sizing to tune, no starvation.

**A `Semaphore`, not unbounded concurrency.** Virtual threads make it *easy* to issue 3,000
concurrent requests during a batch sync. PokeAPI is free and fair-use rate-limited; that is
a self-inflicted outage. The semaphore caps in-flight requests at 16 regardless of how many
tasks exist.

Note that concurrency is bounded on **two** axes, and both are needed. The semaphore bounds
how many calls are in flight; the page-size cap of 100 bounds how many a single request can
queue in the first place. Without the cap, one `size=10000` request would sit there issuing
20,001 calls, sixteen at a time, for several minutes.

**Try-with-resources on the executor.** `close()` blocks until every task completes, so the
method cannot return before its work is done and no task outlives the request.

---

## What we do not use, and why

| Approach | Why not |
|---|---|
| **Structured concurrency (`StructuredTaskScope`)** | Genuinely the right shape for this. Still preview at language level 24, therefore banned — [ADR-0004](../adr/0004-java-24-language-level.md) |
| **Reactive / WebFlux** | Persistence is blocking JPA, so a reactive web layer bridges back to a bounded pool anyway. All the complexity, none of the benefit. Rejected platform-wide |
| **`parallelStream()`** | Uses the shared common ForkJoin pool. Blocking work in it starves every other consumer in the JVM, including things you did not write |
| **A fixed platform thread pool** | Sizing it correctly requires knowing the blocking ratio in advance. Virtual threads remove the question |
| **`@Async` on a use case** | Hides the concurrency boundary behind an annotation and a proxy. Explicit is better where correctness depends on it |

---

## `synchronized` and virtual threads

Do not hold a lock across a blocking call on a virtual thread (`java:S6906`). A pinned
carrier thread defeats the entire point. Prefer `ReentrantLock` if you genuinely need
mutual exclusion — but first ask why shared mutable state exists at all, given the domain
is immutable.

## ThreadLocal

Avoid it. If you must, always clean up in a `finally` (`java:S5164`). With virtual threads,
a leaked `ThreadLocal` leaks per-task rather than per-pool-thread, which is a different and
worse memory profile.

## Timeouts are not optional

```java
RestClient.builder()
    .requestFactory(ClientHttpRequestFactoryBuilder.simple()
        .withCustomizer(f -> {
            f.setConnectTimeout(Duration.ofSeconds(2));
            f.setReadTimeout(Duration.ofSeconds(5));
        }))
    .build();
```

A call with no timeout is a resource leak with a delay fuse. Every outbound call has both,
plus 3 retries with exponential backoff on 5xx and timeouts, **no retry on 4xx**, and a
circuit breaker after 5 consecutive failures.

## Testing concurrent code

- **Never `Thread.sleep`** (`java:S2925`). Inject `ClockPort`.
- Assert the *bound*, not the timing: a test can verify at most 16 concurrent in-flight requests by counting with a `CountDownLatch` in a WireMock stub.
- Test the failure interleaving explicitly — one of 20 calls failing should not fail the page.

## Related

[Java patterns](java-patterns.md) · [Sequence — listing a page](../diagrams/sequence-list-page.md) · [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md)
