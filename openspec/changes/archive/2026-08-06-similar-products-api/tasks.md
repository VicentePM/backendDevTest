# Tasks: Similar Products API

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~800 lines |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Single PR (size-exception selected) |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Full Implementation | PR 1 | `mvn clean verify` | `mvn spring-boot:run` | Revert commit |

## Phase 1: Foundation
- [x] 1.1 Create `pom.xml` with dependencies for Spring Boot 3, WebFlux, Resilience4j, WireMock, JaCoCo, and Spotless.
- [x] 1.2 Create `src/main/resources/application.yml` with port 5000 and upstream config.
- [x] 1.3 Create `src/main/java/com/example/similarproducts/domain/model/ProductDetail.java`.
- [x] 1.4 Create `src/main/java/com/example/similarproducts/domain/model/SimilarProductsQuery.java`.
- [x] 1.5 Create `src/main/java/com/example/similarproducts/domain/model/exception/ProductNotFoundException.java`.
- [x] 1.6 Create `src/main/java/com/example/similarproducts/domain/port/in/GetSimilarProductsUseCase.java`.
- [x] 1.7 Create `src/main/java/com/example/similarproducts/domain/port/out/SimilarProductIdsPort.java`.
- [x] 1.8 Create `src/main/java/com/example/similarproducts/domain/port/out/ProductDetailPort.java`.
- [x] 1.9 Create `src/main/java/com/example/similarproducts/infrastructure/config/AppConfig.java`.
- [x] 1.10 Create `src/main/java/com/example/similarproducts/infrastructure/config/WebClientConfig.java`.

## Phase 2: Core Domain (TDD)
- [x] 2.1 [RED] Create `src/test/java/com/example/similarproducts/domain/service/SimilarProductsServiceTest.java` with a failing test for successful product aggregation.
- [x] 2.2 [GREEN] Create `src/main/java/com/example/similarproducts/domain/service/SimilarProductsService.java` implementing the happy path.
- [x] 2.3 [RED] Add failing test to `SimilarProductsServiceTest` for `ProductNotFoundException` when base product is missing.
- [x] 2.4 [GREEN] Update `SimilarProductsService` to throw `ProductNotFoundException` on missing product.
- [x] 2.5 [RED] Add failing test to `SimilarProductsServiceTest` for skipping products on timeout/failure.
- [x] 2.6 [GREEN] Update `SimilarProductsService` to use `onErrorResume` to skip failures.

## Phase 3: Adapters (TDD)
- [x] 3.1 [RED] Create `src/test/java/com/example/similarproducts/adapter/out/http/ProductApiAdapterTest.java` with a failing test for `fetchSimilarIds`.
- [x] 3.2 [GREEN] Create `src/main/java/com/example/similarproducts/adapter/out/http/dto/ProductDetailResponse.java` and `src/main/java/com/example/similarproducts/adapter/out/http/ProductApiAdapter.java` implementing `SimilarProductIdsPort`.
- [x] 3.3 [RED] Add failing test to `ProductApiAdapterTest` for `fetchDetail` with Resilience4j.
- [x] 3.4 [GREEN] Update `ProductApiAdapter` to implement `ProductDetailPort` with `@CircuitBreaker`.

## Phase 4: Web Layer (TDD)
- [x] 4.1 [RED] Create `src/test/java/com/example/similarproducts/adapter/in/web/SimilarProductsControllerTest.java` with a failing `@WebMvcTest`.
- [x] 4.2 [GREEN] Create `src/main/java/com/example/similarproducts/adapter/in/web/SimilarProductsController.java` returning a 200 JSON list.
- [x] 4.3 [RED] Add a failing test to `SimilarProductsControllerTest` for 404 response on missing product.
- [x] 4.4 [GREEN] Create `src/main/java/com/example/similarproducts/adapter/in/web/GlobalExceptionHandler.java` mapping exception to 404.
- [x] 4.5 Create `src/main/java/com/example/similarproducts/SimilarProductsApplication.java` as the entry point.

## Phase 5: Integration & Verification
- [x] 5.1 [RED] Create `src/test/java/com/example/similarproducts/SimilarProductsIntegrationTest.java` for E2E scenarios.
- [x] 5.2 [GREEN] Ensure integration tests pass.
- [x] 5.3 Verify JaCoCo coverage (≥80%) and run Spotless formatter.
