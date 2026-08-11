# Tasks: pre-release-improvements

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~150-250 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

## Phase 1: CB ignore-exceptions (Group A)

- [x] 1.1 **CONFIG**: Add `ignore-exceptions: [com.example.similarproducts.domain.model.exception.ProductNotFoundException]` under `resilience4j.circuitbreaker.instances.similarIds` in `src/main/resources/application.yml`
- [x] 1.2 **RED**: Add test `whenCircuitBreakerOpen_shouldStillReturn404` in `SimilarProductsIntegrationTest.java`: fire 20 requests resulting in 404s to open CB, assert 21st request returns 404 (not 503).
- [x] 1.3 **GREEN**: Execute `mvn test` to verify the new test and existing scenario 5 pass due to the config change.

## Phase 2: Caffeine cache (Group B)

- [x] 2.1 **CONFIG**: Add `spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine` to `pom.xml`.
- [x] 2.2 **REFACTOR**: Create `src/main/java/com/example/similarproducts/infrastructure/config/CacheConfig.java` with `@EnableCaching` and a `CacheManager` bean config (Caffeine, TTL 30s, max 500).
- [x] 2.3 **CONFIG**: Add `spring.cache.type=caffeine` and `spring.cache.cache-names=productDetails` to `src/main/resources/application.yml`.
- [x] 2.4 **RED**: Add `fetchDetail_whenCalledTwice_shouldHitCache` to `SimilarProductsIntegrationTest.java`: fetch same product twice, verify WireMock stub is called only once.
- [x] 2.5 **GREEN**: Add `@Cacheable("productDetails")` to `fetchDetail` in `ProductApiAdapter.java` and append `.cache()` to the returned `Mono`.
- [x] 2.6 **REFACTOR**: Add `@BeforeEach` to `SimilarProductsIntegrationTest.java` that runs `cacheManager.getCache("productDetails").clear()` to isolate tests.

## Phase 3: API-first codegen (Group C)

- [x] 3.1 **CONFIG**: Add `openapi-generator-maven-plugin` (version 7.8.0, spring-boot, reactive=true, interfaceOnly=true, skipDefaultInterface=true) to `pom.xml`.
- [x] 3.2 **REFACTOR**: Add `tags: [SimilarProducts]` to `similarProducts.yaml` so the generator emits `SimilarProductsApi` instead of `DefaultApi`.
- [x] 3.3 **VERIFY**: Run `mvn clean generate-sources` and verify generated interface compiles in `target/generated-sources/openapi`.
- [x] 3.4 **REFACTOR**: Update `SimilarProductsController.java` to implement `SimilarProductsApi`, remove manual `@GetMapping`, and add domain->generated DTO mapping.
- [x] 3.5 **VERIFY**: Execute `mvn test` to ensure all existing tests pass with no changes.
