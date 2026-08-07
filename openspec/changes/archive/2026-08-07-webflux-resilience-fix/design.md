# Design: WebFlux Migration and Resilience Fixes

## What Changed vs. Original Design

This document amends `2026-08-06-similar-products-api/design.md`. All other decisions
from the original design remain valid. Only the rows below changed.

## Updated Architecture Decisions

| Decision | Was | Now | Rationale |
|---|---|---|---|
| Web layer | Spring MVC (Tomcat), controller calls `.block()` on the reactive pipeline | **Spring WebFlux (Netty)**, controller returns `Mono<ResponseEntity<...>>` directly | `.block()` inside a Tomcat thread holds that thread for the full upstream duration. Under 200 concurrent VUs with slow upstreams (1–50s delays), the thread pool saturates and requests are dropped. WebFlux + Netty processes all connections on event-loop threads — no thread is held during I/O wait. |
| CB placement | `@CircuitBreaker` on `fetchDetail` (detail fan-out) | `@CircuitBreaker` on `fetchSimilarIds` (entry point) | `fetchDetail` errors are already handled by `onErrorResume → Mono.empty()` in the service, so a CB there adds no value and was causing false positives. `fetchSimilarIds` is the single entry call whose failure affects the whole request. |
| CB fallback | `return Mono.error(ex)` — re-throws unconditionally | Preserves `ProductNotFoundException` (→ 404), returns `Mono.just(List.of())` for all other errors | Re-throwing in a fallback negates the CB's purpose: when the CB opens, every call immediately returns a 500. The fallback must return a safe degraded response. |
| Service error handling on `fetchSimilarIds` | None — errors escaped to controller | `onErrorResume` at the `fetchSimilarIds` call site in `SimilarProductsService` | `.timeout(duration, fallback)` does NOT catch upstream errors — it only fires on `TimeoutException`. Any error from `fetchSimilarIds` (CB open, parse error, network refused) bypassed all error handling and produced 500. Adding `onErrorResume` here is the correct defense-in-depth layer. |
| Netty `responseTimeout` | Set to `responseTimeoutMs + 500ms` as secondary safety net | **Removed** | Netty fires at the transport layer, before Reactor operators can apply their fallback values. A Netty timeout → raw 500 with no chance to recover. Reactor operator timeouts (`.timeout(ms)` in the adapter) are sufficient and have proper fallback chains. |
| Connection pool | Default Reactor Netty pool | Explicit `ConnectionProvider`: `maxConnections(500)`, `pendingAcquireMaxCount(1000)`, `pendingAcquireTimeout(4000ms)` | Default `pendingAcquireTimeout` is 45s — requests would block for 45s waiting for a pool slot instead of failing fast. Making this explicit documents intent and prevents unexpected behavior. |

## Updated Data Flow

```
HTTP GET /product/{id}/similar
         │
         ▼
SimilarProductsController          ← returns Mono<ResponseEntity<List<ProductDetail>>>
         │  invokes
         ▼
SimilarProductsService
         │
         ├─ 1) fetchSimilarIds(productId)
         │       @CircuitBreaker(similarIds)
         │       .timeout(2000ms)
         │       404 → ProductNotFoundException (propagates → 404 response)
         │       any other error → onErrorResume → Mono.just([]) → 200 []
         │
         └─ 2) Flux.fromIterable(ids)
                .flatMapSequential(id →
                    fetchDetail(id)
                    .timeout(2000ms)
                    .onErrorResume(_ → Mono.empty()))   ← skip on any per-product error
                .collectList()
                .timeout(3000ms, Mono.just([]))         ← global response cap
```

## Updated Testing

| Layer | Change |
|---|---|
| Controller unit | `@WebMvcTest` → `@WebFluxTest`; `MockMvc` → `WebTestClient` |
| Service unit | Added test: CB-open / non-404 error on `fetchSimilarIds` returns empty list |
| Integration | No change — already used `WebTestClient` and `@SpringBootTest` |
| WireMock port | Changed from hardcoded `:3001` to `dynamicPort()` to avoid conflicts when Docker is running |
