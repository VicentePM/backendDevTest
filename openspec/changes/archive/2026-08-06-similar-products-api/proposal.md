# Proposal: Similar Products API

## Intent
Expose a `/product/{productId}/similar` endpoint to aggregate and serve similar product details for a given product, composing two upstream endpoints. The goal is to provide a unified API that balances performance and resilience for the frontend to render similar product recommendations.

## Scope

### In Scope
- Expose `GET /product/{productId}/similar` endpoint on port 5000.
- Fetch similar product IDs from `GET http://localhost:3001/product/{id}/similarids`.
- Fetch product details from `GET http://localhost:3001/product/{id}` concurrently.
- Enforce a 3-second timeout per product detail call.
- Handle partial failures by silently skipping product details that fail (404/500/timeout) and returning successful ones.
- Preserve the order of products as returned by the `similarids` endpoint.
- Hexagonal architecture: `domain/model`, `domain/port/{in,out}`, `domain/service`, `adapter/in/web`, `adapter/out/http`, `infrastructure/config`.
- Implementation using Spring Boot 3, Java 21, WebClient, and Gradle Kotlin DSL.
- (Optional) Circuit Breaker with Resilience4j.

### Out of Scope
- Authentication/Authorization.
- Caching of responses.
- Database persistence.

## Capabilities

### New Capabilities
- `similar-products`: Aggregation of similar product details from upstream services.

### Modified Capabilities
- None

## Approach
Implement a reactive Hexagonal Architecture in Spring Boot. The Web Adapter handles incoming requests and delegates to the Domain Service. The Domain Service uses an Outbound Port to call upstream APIs via an HTTP Adapter using `WebClient`. We will use `Flux.flatMap` (or `flatMapSequential` / ordered collection) to fetch product details concurrently while preserving order. Timeouts and partial failures (skipping failed elements) will be handled natively with Reactor operators (`timeout()`, `onErrorResume()`).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/.../domain/` | New | Core domain models, ports, and logic |
| `src/main/java/.../adapter/` | New | Web controller and HTTP client adapters |
| `src/main/java/.../infrastructure/` | New | Configuration for WebClient (and optionally Resilience4j) |
| `build.gradle.kts` | Modified | Add WebFlux and Resilience4j dependencies |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Upstream slow responses | High | Enforce 3s timeout per call and use reactive parallel fetching. |
| Upstream partial failures | Medium | Catch exceptions per item and return empty to skip failed products. |
| Overloading upstream | Medium | Limit concurrency if necessary or use circuit breakers (Resilience4j). |

## Rollback Plan
Revert the branch or commit since this is a new greenfield addition. Remove the added endpoints and adapters.

## Dependencies
- Upstream Mock API (`http://localhost:3001`) must be reachable.
- Spring WebFlux (WebClient)
- Optional: Resilience4j

## Success Criteria
- [ ] `GET /product/{productId}/similar` returns `200 OK` with JSON list of product details.
- [ ] Product details that timeout (>3s) or fail (404/500) are excluded from the result list.
- [ ] Results maintain the order of the IDs returned by the `similarids` endpoint.
- [ ] k6 load tests (200 VUs) pass without errors for the implemented endpoints.
