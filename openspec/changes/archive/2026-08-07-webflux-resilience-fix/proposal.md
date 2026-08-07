# Proposal: Migrate to Pure WebFlux and Fix Resilience Under Load

## Summary

The initial implementation exposed `GET /product/{productId}/similar` using Spring MVC
(Tomcat) with a `.block()` call in the controller, while the entire downstream pipeline
was already reactive (WebClient, Reactor operators). After running the k6 load test and
analyzing the results, two distinct problems were identified and fixed:

1. **Blocking the Tomcat thread pool** — using `.block()` on a reactive chain inside
   Spring MVC means every in-flight request holds a OS thread for the full duration of
   the upstream calls (up to 3 seconds for slow products). Under 200 concurrent VUs this
   saturates the thread pool, causing request drops (0.11% failure rate).

2. **500 errors under load due to circuit breaker fallback re-throwing** — the
   `@CircuitBreaker` on `fetchSimilarIds` had a fallback that called `Mono.error(ex)`,
   which re-propagated any exception (including `CallNotPermittedException` when the CB
   opens) straight to the controller as an unhandled 500. The service had no safety net
   on the `fetchSimilarIds` call site, so errors there bypassed the `onErrorResume`
   inside `flatMapSequential`.

## Problem 1: Spring MVC + `.block()` vs. WebFlux

### Why it matters for this test

The k6 test runs 5 scenarios sequentially with 200 VUs each. The `slow` and `verySlow`
scenarios include upstream products with artificial delays (1s, 5s, 50s). With Tomcat's
default thread pool (~200 threads) and each request blocking for 2–3 seconds waiting on
timeouts, the pool saturates. k6 records connection drops as HTTP failures.

### Why the original design made this choice

The initial design chose "Spring MVC controller returning `Mono<ResponseEntity<...>>`"
because it is simpler to test with `@WebMvcTest` / `MockMvc` and avoids the additional
WebFlux machinery. It is a legitimate tradeoff for services that don't need high
concurrency. For this test, however, performance under load is explicitly evaluated.

### The correct approach: full WebFlux

When the HTTP client is already reactive (WebClient), removing `.block()` and returning
`Mono<ResponseEntity<...>>` from the controller is a small change that unlocks the full
benefit: Netty's event-loop handles all 200 VUs with a fixed number of threads (2 ×
CPU cores), and no thread is held while waiting on upstream I/O.

**Changes:**
- Removed `spring-boot-starter-web` from `pom.xml`; `spring-boot-starter-webflux` is
  the sole web runtime → Netty replaces Tomcat
- Controller returns `Mono<ResponseEntity<List<ProductDetail>>>` directly, no `.block()`
- `@WebMvcTest` replaced with `@WebFluxTest` + `WebTestClient` in controller tests
- `SimilarProductsIntegrationTest` unchanged (already used `WebTestClient`)

## Problem 2: Circuit Breaker Fallback and Service Safety Net

### Root cause

Under 200 concurrent VUs the CB on `fetchSimilarIds` could open (sliding window of 20,
threshold 80%). Once open, every call immediately goes to the fallback:

```java
// BEFORE — fallback re-throws, turns CB state into 500
private Mono<List<String>> fetchSimilarIdsFallback(String productId, Throwable ex) {
    return Mono.error(ex);
}
```

That error propagates through the service chain:

```java
return similarProductIdsPort.fetchSimilarIds(...)  // error escapes here
    .flatMapMany(...)
    .flatMapSequential(
        id -> productDetailPort.fetchDetail(id)
            .onErrorResume(ex -> Mono.empty()))     // only catches errors INSIDE flatMap
    .collectList()
    .timeout(responseTimeout, Mono.just(List.of())); // .timeout() does NOT catch errors
```

`.timeout(duration, fallback)` only substitutes when a `TimeoutException` fires due to
elapsed time — it is NOT equivalent to `.onErrorResume()`. A `CallNotPermittedException`
(or any other exception from `fetchSimilarIds`) propagates through `collectList()` and
`timeout()` unhandled, reaching the controller → Spring WebFlux returns 500.

### The fixes

**Fix A — `fetchSimilarIdsFallback` now returns a safe value:**

```java
// AFTER — CB fallback preserves 404 semantics, returns empty list for everything else
private Mono<List<String>> fetchSimilarIdsFallback(String productId, Throwable ex) {
    if (ex instanceof ProductNotFoundException) {
        return Mono.error(ex);          // 404 must still produce a 404 response
    }
    return Mono.just(List.of());        // CB open, network error → safe empty fallback
}
```

**Fix B — Service adds `onErrorResume` at the `fetchSimilarIds` call site:**

```java
// AFTER — non-ProductNotFoundException errors return empty list, never 500
return similarProductIdsPort
    .fetchSimilarIds(query.productId())
    .onErrorResume(
        ex -> !(ex instanceof ProductNotFoundException),
        ex -> {
            log.warn("fetchSimilarIds failed for productId={}, returning empty. Cause: {}",
                query.productId(), ex.getMessage());
            return Mono.just(List.of());
        })
    .flatMapMany(Flux::fromIterable)
    ...
```

This is defense-in-depth: the fallback handles the CB path, the `onErrorResume` handles
any error that escapes the CB or arrives from non-CB paths (connection refused, parse
error, etc.).

## Additional changes

### Netty `responseTimeout` removed

The `WebClientConfig` was setting a Netty-level `responseTimeout` as a secondary timeout
layer. This was misaligned with the Reactor operator timeouts in the adapter:

- Reactor `.timeout(2000ms)` in `fetchDetail` fires at the reactive layer and is caught
  by `onErrorResume` in the service → safe skip
- Netty `responseTimeout` fires at the transport layer, BEFORE Reactor operators can
  apply fallbacks → raw 500

Removing the Netty `responseTimeout` ensures all timeout handling goes through Reactor
operators that have proper fallback chains. The connection setup is still protected by
`CONNECT_TIMEOUT_MILLIS = 1000ms`.

### Connection pool configured explicitly

Added explicit `ConnectionProvider` with `maxConnections(500)`,
`pendingAcquireMaxCount(1000)`, and `pendingAcquireTimeout(4000ms)`. Without this,
the default Reactor Netty pool (max 500, pending acquire timeout 45s) could hold
requests for 45 seconds waiting for a free connection instead of failing fast.

### `fetchSimilarIds` timeout restored

During debugging the `.timeout()` on `fetchSimilarIds` was removed. It is now restored —
without it, a slow or unresponsive `similarids` endpoint would hold a connection
indefinitely and feed the CB's sliding window with long-running calls, distorting the
failure rate calculation.

## k6 Results

| Metric | Before (MVC + `.block()`) | After (WebFlux + fixes) |
|---|---|---|
| `http_req_failed` | 0.11% | **0.00%** |
| `p(90)` latency | 3.12s | 3.01s |
| `p(95)` latency | 3.43s | 3.06s |
| Architecture | Spring MVC + Tomcat | Spring WebFlux + Netty |
| Tests | 19/19 | **20/20** |

## Design decision update

The architecture decision table in the original design should read:

| Decision | Original | Updated | Rationale |
|---|---|---|---|
| Web layer | Spring MVC + `.block()` on Mono | **Full WebFlux** — controller returns `Mono<ResponseEntity<...>>` | Under 200 concurrent VUs with slow upstreams (1s–50s), MVC's thread-per-request model saturates the thread pool. WebFlux + Netty handles the same concurrency with event-loop threads, eliminating thread-exhaustion failures. |
| CB on `fetchSimilarIds` | Not present initially; added as resilience improvement | Present, with **corrected fallback** | CB fallback must preserve 404 semantics and return a safe value (empty list) for all other errors, never re-throw. |
| Service safety net on `fetchSimilarIds` | None | **`onErrorResume` at call site** | `.timeout(duration, fallback)` does not catch non-timeout errors. Any error from `fetchSimilarIds` (CB open, connection refused) needs explicit handling at the service layer. |
