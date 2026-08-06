# Design: Similar Products API

## Technical Approach

Greenfield Spring Boot 3 / Java 21 service exposing `GET /product/{productId}/similar` on port 5000. Hexagonal architecture isolates domain from Spring MVC and WebClient. Reactive `Flux.flatMap` fans out parallel detail calls; per-call timeout + `onErrorResume` implement skip-on-failure. Resilience4j circuit breaker guards the detail port. Returns `404` only when the similarids upstream returns `404`.

## Architecture Decisions

| Decision | Choice | Alternatives | Rationale |
|---|---|---|---|
| Build tool | Maven | Gradle | User confirmed |
| HTTP client | Spring WebClient (WebFlux) | RestTemplate, OkHttp | Non-blocking parallel fan-out; native `Flux` composition |
| Concurrency | `Flux.flatMap` (unbounded to N ids) | CompletableFuture + ExecutorService | Backpressure-aware, idiomatic with WebClient |
| Per-call timeout | 3s via `Mono.timeout` (configurable) | Global WebClient timeout | Per-call granularity for skip-on-failure |
| Partial failure | `onErrorResume(_ -> Mono.empty())` for 404/500/timeout | Fail whole request | k6 scenarios 2/3/4/5 require partial success |
| Circuit breaker | Resilience4j on `ProductDetailPort` only | On both ports, none | 404 from similarids is meaningful; details are the fan-out hot path |
| Web layer | Spring MVC controller returning `Mono<ResponseEntity<...>>` | Full WebFlux router | Simpler, works alongside WebFlux client |
| Exception mapping | `@RestControllerAdvice` → 404 for `ProductNotFoundException` | Inline in controller | Keeps controller thin |
| Testing | JUnit 5 + Mockito + WireMock; JaCoCo ≥80% | Real "simulado" upstream | k6 owns E2E; WireMock owns integration |

## Data Flow

```
    HTTP GET /product/{id}/similar
             │
             ▼
    SimilarProductsController (adapter/in/web)
             │  invokes
             ▼
    GetSimilarProductsUseCase ── impl ──► SimilarProductsService (domain/service)
             │                                    │
             │ 1) SimilarProductIdsPort.fetch(id)  │  → String[]  (404 → ProductNotFoundException)
             │ 2) Flux.fromArray(ids)              │
             │    .flatMap(ProductDetailPort::fetch│    // parallel, 3s timeout, skip on error)
             │    .collectList()                   │
             ▼                                    ▼
    ProductApiAdapter (adapter/out/http) ──► WebClient ──► http://localhost:3001
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `pom.xml` | Create | Spring Boot 3, Java 21, webflux, web, resilience4j-spring-boot3, wiremock, jacoco, spotless |
| `src/main/resources/application.yml` | Create | `server.port=5000`, upstream base URL, timeouts, resilience4j config |
| `src/main/java/.../SimilarProductsApplication.java` | Create | `@SpringBootApplication` entry point |
| `.../domain/model/ProductDetail.java` | Create | `record ProductDetail(String id, String name, BigDecimal price, Boolean availability)` |
| `.../domain/model/SimilarProductsQuery.java` | Create | `record SimilarProductsQuery(String productId)` |
| `.../domain/model/exception/ProductNotFoundException.java` | Create | Domain exception for 404 from similarids |
| `.../domain/port/in/GetSimilarProductsUseCase.java` | Create | Use-case interface |
| `.../domain/port/out/SimilarProductIdsPort.java` | Create | Fetch similar IDs |
| `.../domain/port/out/ProductDetailPort.java` | Create | Fetch single product detail |
| `.../domain/service/SimilarProductsService.java` | Create | Orchestrates ports, timeout + skip-on-failure |
| `.../adapter/in/web/SimilarProductsController.java` | Create | `@GetMapping("/product/{productId}/similar")` |
| `.../adapter/in/web/GlobalExceptionHandler.java` | Create | Maps `ProductNotFoundException` → 404 |
| `.../adapter/out/http/ProductApiAdapter.java` | Create | Implements both out ports with WebClient + Resilience4j |
| `.../adapter/out/http/dto/ProductDetailResponse.java` | Create | Upstream DTO → mapped to domain |
| `.../infrastructure/config/WebClientConfig.java` | Create | WebClient bean with base URL and connection timeouts |
| `.../infrastructure/config/AppConfig.java` | Create | `@ConfigurationProperties` for timeout/base URL |
| `src/test/**` | Create | Unit (service, controller), integration (WireMock), see Testing |

## Interfaces / Contracts

```java
public interface GetSimilarProductsUseCase {
    Mono<List<ProductDetail>> handle(SimilarProductsQuery query);
}

public interface SimilarProductIdsPort {
    Mono<List<String>> fetchSimilarIds(String productId); // errors: ProductNotFoundException on 404
}

public interface ProductDetailPort {
    Mono<ProductDetail> fetchDetail(String productId);    // errors: propagate; caller skips
}
```

HTTP response body: `[{ "id": "1", "name": "Dress", "price": 19.99, "availability": true }, ...]`

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `SimilarProductsService`: happy path, similarids 404 → exception, per-call timeout skip, 404/500 skip, empty ids | JUnit 5 + Mockito; mock both ports returning `Mono`/`Flux` via `StepVerifier` |
| Unit | `SimilarProductsController` + `GlobalExceptionHandler` | `@WebMvcTest` mocking use case; assert 200 body and 404 mapping |
| Integration | End-to-end wiring with stubbed upstream on port 3001 | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + WireMock on 3001; cover k6 scenarios 1–5 |
| E2E | Behavioral scenarios (given) | k6 via `docker-compose run --rm k6 run scripts/test.js` — not authored here |
| Coverage | ≥80% lines | JaCoCo Maven plugin, fail build below threshold |
| Style | Format + lint | Spotless (google-java-format) in `verify` phase |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. Only outbound HTTP to a documented upstream.

## Migration / Rollout

No migration required. Greenfield service; deployed standalone on port 5000.

## Open Questions

- None. All decisions confirmed in the proposal context.
