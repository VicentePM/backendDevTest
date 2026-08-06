# Exploration: similar-products-api

## Current State

This is a greenfield project — no application code exists yet. Only the test harness, API contracts, and project description are provided:

- `similarProducts.yaml` — API contract for the new endpoint (port 5000)
- `existingApis.yaml` — upstream mock API contract (port 3001, simulado)
- `docker-compose.yaml` — simulado (mock), k6, influxdb, grafana
- `shared/simulado/mocks.json` — upstream mock data (reveals real test scenarios)
- `shared/k6/test.js` — k6 load test (5 scenarios, 200 VUs each)

## Affected Areas

All files will be created from scratch. The project root will host the Spring Boot application.

- `src/main/java/...` — application code (hexagonal structure, TBD)
- `pom.xml` or `build.gradle` — build descriptor
- `src/test/java/...` — unit + integration tests

## Critical Discovery: Mock Data Reveals Real Test Scenarios

The k6 test scenarios map directly to mock behaviors — this is the heart of the resilience challenge:

| k6 scenario | productId | similarIds returned | upstream detail behavior |
|-------------|-----------|---------------------|--------------------------|
| `normal`    | 1         | [2, 3, 4]           | product 3 has 100ms delay |
| `slow`      | 2         | [3, 100, 1000]      | product 100: 1000ms, product 1000: 5000ms |
| `verySlow`  | 3         | [100, 1000, 10000]  | product 10000: **50000ms delay** |
| `notFound`  | 4         | [1, 2, 5]           | product 5 returns **404** |
| `error`     | 5         | [1, 2, 6]           | product 6 returns **500** |

**Key implications:**
1. **Parallel fetch is mandatory** — serial fetching of similar IDs would compound delays catastrophically (verySlow: 50s+ serially)
2. **Timeout per upstream call is mandatory** — product 10000 at 50s would exhaust thread pools / event loops without a timeout
3. **Partial results strategy** — when one similar product returns 404 or 500, the options are: skip it, or fail the whole request. Given evaluation criteria (resilience), skipping degraded products is the correct choice.
4. **similarids endpoint has no documented 404** — the upstream spec doesn't define a 404 for `/similarids`. When the main product doesn't exist, the mock may return 404 on `/product/{id}/similarids` (not explicitly mocked — needs defensive handling).

## Approach Analysis

### 1. HTTP Client: WebClient (reactive/non-blocking) — RECOMMENDED

Spring WebFlux `WebClient` with `Mono.zip` / `Flux.flatMap` for parallel calls.

- **Pros**:
  - Non-blocking I/O — handles 200 VUs with far fewer threads than blocking alternatives
  - `flatMap` with `concatMap` / `merge` gives fine-grained parallelism control
  - `timeout()` operator per-call is first-class
  - `.onErrorResume()` for graceful partial degradation per product detail call
  - Best fit for the k6 load test at 200 VUs × 5 scenarios concurrently
  - Spring Boot 3 idiomatic (works in both MVC and WebFlux)
- **Cons**:
  - Reactive programming model has a steeper learning curve
  - Stack traces are harder to read
  - Must avoid blocking calls in the reactive pipeline
- **Effort**: Medium (reactive patterns required)

### 2. HTTP Client: RestClient (Spring Boot 3.2+) — VIABLE ALTERNATIVE

Spring 6.1's `RestClient` is the blocking successor to `RestTemplate`.

- **Pros**:
  - Familiar imperative programming model
  - Cleaner API than `RestTemplate`
  - Easy to understand and review
- **Cons**:
  - Blocking — one thread per in-flight request
  - Parallel calls require explicit `CompletableFuture` / `ExecutorService` orchestration
  - Under 200 VUs load, thread pool exhaustion is a real risk without careful sizing
  - More boilerplate for timeout + partial failure handling
- **Effort**: Low to Medium (but parallel/resilience wiring is manual)

### 3. HTTP Client: RestTemplate — NOT RECOMMENDED

Legacy. Deprecated in Spring 6.1 in favor of `RestClient`. No new projects should start with it.

---

**Decision**: Use `WebClient`. The k6 load test with 50s upstream delays under 200 VUs is precisely the scenario where non-blocking wins decisively. Reactive also enables declarative timeout + fallback composition.

Note: Even though `WebClient` is from WebFlux, it can be used in a standard Spring MVC (`spring-boot-starter-web`) application — just add `spring-boot-starter-webflux` as a dependency and keep the MVC dispatcher. This avoids the full reactive stack while gaining the non-blocking HTTP client.

---

### 4. Build Tool: Gradle — RECOMMENDED

| | Maven | Gradle |
|-|-|-|
| Configuration | XML verbose | Groovy/Kotlin DSL — concise |
| Build speed | Slower (no incremental by default) | Faster (incremental, build cache) |
| Spring Boot support | First-class (`spring-boot-maven-plugin`) | First-class (`spring-boot-gradle-plugin`) |
| Ecosystem familiarity | Universal | Growing — standard for Android, common in Java |
| IDE support | Universal | Universal (IntelliJ / Eclipse) |
| Multi-module | Verbose | Clean with `settings.gradle` |

For a single-module project like this, either works. However, Gradle's Kotlin DSL gives a cleaner, more maintainable build file. **Gradle (Kotlin DSL)** is recommended; if the evaluator environment is Maven-centric, Maven is a safe fallback.

---

## Hexagonal Architecture Package Structure

```
com.example.similarproducts/
├── domain/
│   ├── model/
│   │   └── ProductDetail.java          # Pure domain object (no framework annotations)
│   ├── port/
│   │   ├── in/
│   │   │   └── GetSimilarProductsUseCase.java   # Input port (interface)
│   │   └── out/
│   │       └── ProductRepository.java           # Output port (interface)
│   └── service/
│       └── SimilarProductsService.java          # Domain service (use case impl)
├── application/
│   └── (optional: application services, command/query objects)
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── SimilarProductsController.java   # REST controller (drives use case)
│   │       └── ProductDetailResponse.java       # Response DTO (optional, same schema)
│   └── out/
│       └── http/
│           ├── ProductApiClient.java            # WebClient calls to port 3001
│           └── ProductApiMapper.java            # Maps HTTP response → domain model
└── infrastructure/
    └── config/
        ├── WebClientConfig.java                 # Bean config for WebClient
        └── AppConfig.java                       # Property bindings
```

**Rationale**: Domain knows nothing about HTTP or Spring. Ports are interfaces. Adapters implement/drive ports. This makes unit-testing the domain service trivial (mock the output port).

---

## Edge Cases and Resilience Requirements

### Edge Case Matrix

| Scenario | Input | Expected behavior |
|----------|-------|-------------------|
| Product exists, all similar products healthy | `/product/1/similar` | 200 with array of 3 products |
| Product does not exist | `/product/{unknown}/similar` | 404 — upstream `/similarids` returns 404 |
| One similar product returns 404 | product 5's similar → product 5 returns 404 | **Skip** that product, return remaining |
| One similar product returns 500 | product 5's similar → product 6 returns 500 | **Skip** or treat as unavailable |
| One similar product is very slow (50s) | product 3's similar → product 10000 | **Timeout** that call, skip or return partial |
| All similar products fail/timeout | all details fail | Return empty array `[]` or 200 with empty |
| similarids returns empty array | product has no similar | Return 200 with `[]` |
| similarids itself is slow or fails | upstream down | 503 or timeout propagation |

### Resilience Design Decisions

1. **Per-detail timeout**: Each `GET /product/{id}` call MUST have a timeout (recommended: 2s based on mock data — product 100 at 1s is borderline, product 1000 at 5s should be cut). The exact threshold is a config value.
2. **Partial degradation**: A 404 or timeout on an individual similar product detail MUST NOT fail the whole request. Skip that product and return the rest.
3. **similarids 404 → propagate as 404**: If the main product has no similar IDs (upstream returns 404 on `/similarids`), our API MUST return 404. This is the "product not found" contract.
4. **No circuit breaker for MVP**: Resilience4j circuit breaker would improve resilience further but adds complexity. The upstream already has WireMock for tests; circuit breaker is an optional enhancement.
5. **Order preservation**: The spec says "ordered by similarity". The upstream returns IDs in order; parallel fetch may reorder. Solution: fetch in parallel, then reorder results by original ID order.

---

## Open Questions

1. **What happens when `similarids` returns 404?** — The mock doesn't define this for unknown products. Assumption: treat any non-200 from `/similarids` as 404 on our side. Needs verification with evaluator if ambiguous.
2. **Empty array vs. 404 for zero similar products?** — If `/similarids` returns `[]`, we return `200 []`. This differs from "product not found". Confirmed by spec (minItems: 0).
3. **Detail timeout value** — Config-driven. Default recommendation: 3000ms. Products with 5s+ delays (1000, 10000) will be skipped under this threshold.

---

## Recommendation Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| HTTP Client | `WebClient` (reactive) | Non-blocking, native timeout+fallback operators, k6 resilience scenarios require it |
| Build tool | Gradle (Kotlin DSL) | Cleaner DSL, faster builds |
| Architecture | Hexagonal | Required by spec; enables clean port/adapter testing |
| Parallelism | `Flux.flatMap` with concurrency control | Fetch all similar details in parallel |
| Partial failure | Skip failed/timed-out details | Resilience over completeness |
| Timeout | Configurable, default 3s per detail call | Covers normal+slow scenarios, cuts verySlow |
| Spring stack | Spring MVC + WebClient (not full WebFlux) | Simpler controller model, non-blocking client |

### Ready for Proposal
Yes — requirements, architecture approach, and key design decisions are fully understood. The proposal should capture: hexagonal structure, WebClient with parallel fetch, timeout/partial-failure strategy, Gradle build, and test layers (unit + WireMock integration + k6 e2e).
