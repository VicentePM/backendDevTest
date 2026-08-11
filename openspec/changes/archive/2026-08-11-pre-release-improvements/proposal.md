# Proposal: pre-release-improvements

## Intent
Address three pre-release improvements to enhance robustness, performance, and contract compliance. Specifically: fixing a circuit breaker flaw that breaks the API contract under 404 floods, caching product details to improve response times, and enforcing API-first development via OpenAPI generation.

## Scope

### In Scope
- Configure Resilience4j to ignore 404 (`ProductNotFoundException`) so the circuit breaker remains closed during expected 404 responses.
- Implement a Caffeine cache (via Spring Cache `@Cacheable`) on the `fetchDetail` adapter method with a 30s TTL and 500 entry max size.
- Add `openapi-generator-maven-plugin` to generate the `SimilarProductsApi` interface from `similarProducts.yaml`.
- Refactor `SimilarProductsController` to implement the generated interface and map generated DTOs to Domain models.

### Out of Scope
- Modifying core domain logic or domain models.
- Changes to the external mock API behavior.
- Caching the `fetchSimilarIds` call (caching is limited to individual product details).

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- None (Resilience and caching are non-functional requirement improvements; the external API contract remains exactly as defined).

## Approach

1. **Circuit Breaker**: Add `ignore-exceptions: [com.example.similarproducts.domain.model.exception.ProductNotFoundException]` to `application.yml` under the `similarIds` Resilience4j instance. Add a regression test for CB-open + 404s.
2. **Cache**: Add Caffeine dependency and `@EnableCaching`. Apply `@Cacheable("productDetails")` to `RestProductRepository.fetchDetail` (caches the reactive `Mono`). Configure TTL to 30s in `application.yml`. Ensure cache is reset between integration tests.
3. **API-First**: Configure `openapi-generator-maven-plugin` (`interfaceOnly=true`, `reactive=true`) pointing to `${project.basedir}/../similarProducts.yaml`. Update `SimilarProductsController` to implement `SimilarProductsApi`, mapping the generated `ProductDetail` DTO to the domain `ProductDetail` at the adapter boundary to prevent domain contamination.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `similarProducts/src/main/resources/application.yml` | Modified | Add CB ignore-exceptions and cache TTL config |
| `similarProducts/pom.xml` | Modified | Add Caffeine and OpenAPI Generator plugin |
| `similarProducts/src/main/java/com/example/similarproducts/adapter/out/http/ProductApiAdapter.java` | Modified | Add `@Cacheable` to `fetchDetail` |
| `similarProducts/src/main/java/com/example/similarproducts/adapter/in/web/SimilarProductsController.java` | Modified | Implement generated interface, map DTOs |
| `similarProducts/src/main/java/com/example/similarproducts/config/CacheConfig.java` | New | Add `@EnableCaching` |
| `similarProducts/src/test/...` | Modified | Add regression test for CB 404s and cache hits |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Test flakiness due to cache state | Medium | Clear cache between tests or use `@DirtiesContext` in integration tests |
| Generated model drifts from Domain | Medium | Isolate generated DTOs strictly in the web adapter; map explicitly to Domain model |

## Rollback Plan
Revert the commit containing these changes. The changes are strictly scoped to the `similarProducts` module configuration and adapters, and do not require external database schema or data migrations.

## Dependencies
- The `similarProducts.yaml` OpenAPI specification file must be present at `${project.basedir}/../similarProducts.yaml`.

## Success Criteria
- [ ] 404 responses from the upstream API do not contribute to the Circuit Breaker failure rate.
- [ ] Repeated calls for the same Product Detail within 30 seconds only hit the external API once.
- [ ] Controller implements the generated OpenAPI interface instead of manual `@GetMapping` annotations.
- [ ] All existing and new tests pass (including a new test for the CB 404 scenario).
