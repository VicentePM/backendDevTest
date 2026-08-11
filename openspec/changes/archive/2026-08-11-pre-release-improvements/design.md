# Design: pre-release-improvements — CB Ignore, Caching, API-First

## Technical Approach

Three orthogonal changes to `similarProducts/` addressing pre-release quality improvements. Each change is scoped to its own layer:

1. **Change 1** — pure configuration (`application.yml`) plus a new integration test. No Java changes.
2. **Change 2** — Spring Cache abstraction with Caffeine backing, applied at the outbound adapter (`ProductApiAdapter.fetchDetail`). Cache lives in the adapter layer, transparent to domain and use case.
3. **Change 3** — Introduce `openapi-generator-maven-plugin` at build time. Controller implements the generated `SimilarProductsApi` interface. Domain model stays untouched; a mapping is added at the adapter-in boundary.

All three preserve the hexagonal boundaries and do not require domain-layer edits.

## Architecture Decisions

| Decision | Options | Choice | Rationale |
|---|---|---|---|
| Where CB ignore is declared | YAML config vs. `@CircuitBreaker(ignoreExceptions=...)` | **YAML** | Centralised, no annotation churn, matches existing config style. |
| Cache backend | Caffeine vs. ConcurrentMapCacheManager | **Caffeine** | TTL + size bounds; production-grade; the reviewer named it explicitly. |
| `@EnableCaching` location | `WebClientConfig` vs. new `CacheConfig` | **New `CacheConfig.java`** | Single-responsibility. `WebClientConfig` is HTTP-only; mixing cache concerns would violate the current config layout. |
| Cache-Mono semantics | Manual `AsyncCache` vs. `@Cacheable` on `Mono` | **`@Cacheable` on `Mono`** | Spring Cache stores the `Mono` publisher itself. Since WebClient `Mono`s are cold + replayable via `.cache()` isn't needed here because Spring returns the same `Mono` instance which, once materialised, keeps its value in the cache entry for subsequent subscribes. Documented inline. |
| Cache reset in tests | `@DirtiesContext` vs. `cacheManager.getCache(...).clear()` | **`cacheManager.getCache("productDetails").clear()` in `@BeforeEach`** | `@DirtiesContext` rebuilds the whole Spring context per test (~5s each × 6 tests). Explicit clear is O(ms). |
| Generated model vs. domain model | Reuse generated model as domain vs. keep both | **Keep both, map at controller** | Preserves hexagonal purity. Generated types belong to the inbound adapter only. |
| Generator config | `interfaceOnly=true`, `skipDefaultInterface=true` | **Both true** | We implement the interface; default methods would hide unimplemented endpoints as `501`. |

## Data Flow

```
HTTP GET /product/{id}/similar
        │
        ▼
SimilarProductsController  ── implements ──► generated SimilarProductsApi
        │                                        (from similarProducts.yaml)
        ▼
GetSimilarProductsUseCase (domain)
        │
        ▼
ProductApiAdapter.fetchSimilarIds  ──► CB "similarIds"
        │                              ignore: ProductNotFoundException
        ▼
ProductApiAdapter.fetchDetail  ──► @Cacheable("productDetails")
        │                              Caffeine: 30s TTL, max 500
        ▼
Upstream service (WireMock in tests)
```

## File Changes

| File | Action | Description |
|---|---|---|
| `similarProducts/src/main/resources/application.yml` | Modify | Add `ignore-exceptions` to CB; add `spring.cache` block. |
| `similarProducts/pom.xml` | Modify | Add `spring-boot-starter-cache`, `caffeine`, `openapi-generator-maven-plugin`. |
| `similarProducts/src/main/java/.../infrastructure/config/CacheConfig.java` | Create | `@Configuration @EnableCaching`. |
| `similarProducts/src/main/java/.../adapter/out/http/ProductApiAdapter.java` | Modify | Add `@Cacheable("productDetails")` on `fetchDetail` with explanatory comment. |
| `similarProducts/src/main/java/.../adapter/in/web/SimilarProductsController.java` | Modify | Implement generated `SimilarProductsApi`; remove `@GetMapping`; map domain → generated `ProductDetail`. |
| `similarProducts/src/test/java/.../SimilarProductsIntegrationTest.java` | Modify | Add CB-open + 404 test, add cache-hit test, inject `CacheManager` for reset. |

## Interfaces / Contracts

### 1. `application.yml` — full replacement

```yaml
server:
  port: 5000

upstream:
  base-url: http://localhost:3001
  timeout-ms: 2000
  response-timeout-ms: 3000

spring:
  cache:
    type: caffeine
    cache-names: productDetails
    caffeine:
      spec: maximumSize=500,expireAfterWrite=30s

resilience4j:
  circuitbreaker:
    instances:
      similarIds:
        sliding-window-size: 20
        failure-rate-threshold: 80
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 10
        register-health-indicator: true
        ignore-exceptions:
          - com.example.similarproducts.domain.model.exception.ProductNotFoundException
```

### 2. `pom.xml` — new dependencies

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

### 3. `pom.xml` — openapi-generator plugin block

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>7.8.0</version>
  <executions>
    <execution>
      <id>generate-similar-products-api</id>
      <phase>generate-sources</phase>
      <goals><goal>generate</goal></goals>
      <configuration>
        <inputSpec>${project.basedir}/../similarProducts.yaml</inputSpec>
        <generatorName>spring</generatorName>
        <library>spring-boot</library>
        <output>${project.build.directory}/generated-sources/openapi</output>
        <apiPackage>com.example.similarproducts.adapter.in.web.generated.api</apiPackage>
        <modelPackage>com.example.similarproducts.adapter.in.web.generated.model</modelPackage>
        <configOptions>
          <useSpringBoot3>true</useSpringBoot3>
          <reactive>true</reactive>
          <interfaceOnly>true</interfaceOnly>
          <skipDefaultInterface>true</skipDefaultInterface>
          <useTags>true</useTags>
          <openApiNullable>false</openApiNullable>
        </configOptions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 4. `CacheConfig.java` — new file

```java
package com.example.similarproducts.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {}
```

### 5. `ProductApiAdapter.fetchDetail` — annotated

```java
@Override
@Cacheable("productDetails")
public Mono<ProductDetail> fetchDetail(String productId) {
  // NOTE: Spring Cache stores the Mono publisher instance itself, keyed by productId.
  // Subsequent subscriptions to the cached Mono short-circuit the WebClient call
  // because the underlying HTTP response is memoised inside the WebClient pipeline
  // once materialised. TTL (30s) and maxSize (500) are set in application.yml.
  return webClient
      .get()
      .uri("/product/{id}", productId)
      .retrieve()
      .onStatus(
          status -> status.isError(),
          response ->
              Mono.error(
                  new WebClientResponseException(
                      response.statusCode().value(),
                      "Upstream error for product " + productId,
                      null, null, null)))
      .bodyToMono(ProductDetailResponse.class)
      .map(dto -> new ProductDetail(dto.id(), dto.name(), dto.price(), dto.availability()))
      .timeout(Duration.ofMillis(timeoutMs))
      .cache(); // ensure replay-on-subscribe semantics for cached Mono
}
```

### 6. `SimilarProductsController` — implements generated API

```java
package com.example.similarproducts.adapter.in.web;

import com.example.similarproducts.adapter.in.web.generated.api.DefaultApi;
import com.example.similarproducts.adapter.in.web.generated.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

@RestController
public class SimilarProductsController implements DefaultApi {

  private final GetSimilarProductsUseCase useCase;

  public SimilarProductsController(GetSimilarProductsUseCase useCase) {
    this.useCase = useCase;
  }

  @Override
  public Mono<ResponseEntity<Flux<ProductDetail>>> getProductSimilar(
      String productId, ServerWebExchange exchange) {
    return useCase
        .handle(new SimilarProductsQuery(productId))
        .map(list -> ResponseEntity.ok(Flux.fromIterable(list).map(this::toGeneratedModel)));
  }

  private ProductDetail toGeneratedModel(
      com.example.similarproducts.domain.model.ProductDetail d) {
    return new ProductDetail()
        .id(d.id())
        .name(d.name())
        .price(d.price())
        .availability(d.availability());
  }
}
```

**Schema alignment check**: Spec `ProductDetail { id: string, name: string, price: number, availability: boolean }` — required all four. Domain record uses the same four fields. `price: number` (no format) generates `java.math.BigDecimal` in openapi-generator. If the domain uses `double`, the mapping compiles via `BigDecimal.valueOf(d.price())`; if `BigDecimal`, direct assignment. The mapper handles it at the boundary.

**Generated interface name**: If the spec has no `tags`, the generator emits `DefaultApi`. If `useTags=true` is preferred with a tag, add `tags: [SimilarProducts]` to the operation in the spec. The design uses `DefaultApi` to avoid spec edits.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Integration (new) | CB opens after 20 × 404, next 404 still returns HTTP 404 | Stub `/product/999/similarids` → 404; fire 20 requests; assert 21st still returns 404 (not 200 []). |
| Integration (regression) | Scenario 5 still passes | No change; existing test remains green. |
| Integration (new) | Cache hit: 2 requests for same productId → only 1 WireMock invocation to `/product/{id}` | Stub upstream; issue two `/product/1/similar` calls with same similar-id list; `wireMock.verify(1, getRequestedFor(...))` on the detail URL. |
| Integration (cache reset) | Isolation between tests | Inject `CacheManager`; in `@BeforeEach` call `cacheManager.getCache("productDetails").clear()`. Avoid `@DirtiesContext` for speed. |
| Build | Generator produces `DefaultApi` interface at `target/generated-sources/openapi/...` | `mvn compile` — controller compiles only if it implements the generated signature correctly. |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary introduced.

## Migration / Rollout

No migration required. All changes are backward-compatible at the HTTP contract level. Deploy in the recommended order (CB → cache → codegen) to minimise blast radius per PR.

## Gotchas

- **Cache + reactive**: forgetting `.cache()` on the inner `Mono` can cause a new HTTP call on each subscription of the cached publisher. The `.cache()` operator memoises the emitted value.
- **CB metrics**: with `ignore-exceptions`, 404s no longer show up in `resilience4j.circuitbreaker.calls{kind="failed"}`. This is intentional but worth calling out in metrics dashboards.
- **Generator IDE support**: after first `mvn compile`, mark `target/generated-sources/openapi/src/main/java` as a source root in the IDE. CI is unaffected.
- **Spec path**: `${project.basedir}/../similarProducts.yaml` assumes the spec stays at repo root. If it moves, the build breaks — document in the module README.
- **Reactive `Flux` in response**: openapi-generator's reactive spring template returns `Mono<ResponseEntity<Flux<T>>>` for array responses. Existing tests using `jsonPath` still pass because Spring serialises the `Flux` as a JSON array.

## Open Questions

- [ ] Confirm cache TTL: 30s appropriate for product details, or should a different value be considered?
- [ ] Should the OpenAPI spec gain an explicit `tags: [SimilarProducts]` to produce a named interface (`SimilarProductsApi`) instead of `DefaultApi`?
