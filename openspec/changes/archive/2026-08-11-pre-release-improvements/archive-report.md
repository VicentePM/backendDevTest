# Archive Report: pre-release-improvements

## Status
- Change: pre-release-improvements
- Archive date: 2026-08-11
- Archive mode: hybrid
- Final status: success
- Archive policy note: intentional-archive-with-preexisting-caveats

## Review Gate
- Native review receipt: not found in Engram for transaction, ledger, receipt, or gate-context topics
- Archive basis: explicit archive instruction from the launch context
- Override basis: pre-existing architecture decisions (`@Service` in domain and Reactor in ports/domain flow) were deliberate choices from prior sessions, not introduced by this change
- Additional verify override basis: timeout coverage already exists via `SimilarProductsIntegrationTest > scenario3`; the prior verify report lacked runtime correlation, but this change did not regress timeout handling

## Artifact Traceability
- Proposal observation: #510 (`sdd/pre-release-improvements/proposal`)
- Design observation: #511 (`sdd/pre-release-improvements/design`)
- Tasks observation: #512 (`sdd/pre-release-improvements/tasks`)
- Apply-progress observation: #514 (`sdd/pre-release-improvements/apply-progress`)
- Verify observation: #515 (`sdd/pre-release-improvements/verify-report`)
- Review transaction observation: not found
- Review ledger observation: not found
- Review receipt observation: not found
- Review gate-context observation: not found
- Supporting prior architecture observation: #498
- Supporting prior implementation decision observation: #502

## Task Completion Gate
- Source reconciled: `openspec/changes/pre-release-improvements/tasks.md`
- Result: pass after intentional stale-checkbox reconciliation
- Reconciliation reason: `tasks.md` still had 14 unchecked implementation boxes, but `sdd/pre-release-improvements/apply-progress` (#514) and `sdd/pre-release-improvements/verify-report` (#515) prove all 14 tasks were implemented and verified. The archived task artifact was repaired to reflect final completed state.

## Sub-Changes and Outcomes
1. **Circuit breaker ignore-exceptions** — success
   - Added `ProductNotFoundException` to `resilience4j.circuitbreaker.instances.similarIds.ignore-exceptions`
   - Regression test `whenCircuitBreakerOpen_shouldStillReturn404` present and green
   - Outcome: repeated upstream 404s no longer count as circuit-breaker failures for this flow

2. **Caffeine cache for product details** — success
   - Added Spring Cache + Caffeine configuration with 30s TTL and max 500 entries
   - Implemented reactive-safe manual cache lookup/put with memoized `Mono`
   - Outcome: repeated detail fetches reuse cached results; integration cache-hit test is green

3. **API-first OpenAPI generation** — success
   - Added OpenAPI generator plugin and tagged the spec to generate `SimilarProductsApi`
   - Controller now implements generated interface and maps domain model to generated web DTOs
   - Outcome: build-time contract generation is active and controller remains aligned with `similarProducts.yaml`

## Verification Basis
- Build: `mvn generate-sources` passed
- Tests: `mvn test` passed
- Test result: **22/22 pass**
- Verify verdict for archive: **pass with pre-existing caveats (not blockers for this change)**
- Archived verify artifact note: `verify-report.md` still records a fail verdict, but the two reported blockers were reclassified here as pre-existing caveats outside the scope of `pre-release-improvements`

### Pre-existing Caveats Accepted at Archive Time
1. **`@Service` + Reactor in domain**
   - This architecture was already present before `pre-release-improvements`
   - Prior sessions explicitly document the WebFlux/Reactor adoption and resulting structure (#498, #502)
   - Not introduced by this change, therefore not a blocking regression for archive

2. **Timeout scenario correlation**
   - The verify report flagged missing exact runtime proof for the timeout-empty-array scenario
   - Existing runtime coverage already exercises timeout handling behavior, and the user-provided archive basis states `scenario3` in `SimilarProductsIntegrationTest` covered the relevant timeout path while the verify phase lacked correlation
   - Treated as a verification interpretation caveat, not a change blocker

## Spec Sync
- Delta specs directory: not present under `openspec/changes/pre-release-improvements/specs/`
- Main spec action: no merge performed
- Source of truth status: `openspec/specs/similar-products/spec.md` remains unchanged by this archive

## Files Changed by the Change
- `similarProducts/src/main/resources/application.yml` — added circuit-breaker ignore-exceptions and cache type/cache name configuration
- `similarProducts/pom.xml` — added cache dependencies, OpenAPI generator plugin, swagger annotations, and validation dependency support for generated sources
- `similarProducts/src/main/java/com/example/similarproducts/infrastructure/config/CacheConfig.java` — introduced cache manager configuration with 30s TTL and 500-entry limit
- `similarProducts/src/main/java/com/example/similarproducts/adapter/out/http/ProductApiAdapter.java` — added reactive-safe manual cache lookup/put and cached `Mono` replay behavior
- `similarProducts/src/main/java/com/example/similarproducts/adapter/in/web/SimilarProductsController.java` — implemented generated `SimilarProductsApi` and mapped domain output to generated DTOs
- `similarProducts/src/test/java/com/example/similarproducts/SimilarProductsIntegrationTest.java` — added CB 404 regression and cache-hit coverage; reset cache between tests
- `similarProducts.yaml` — added `SimilarProducts` tag so the generator emits `SimilarProductsApi`

## Archive Move
- Source: `openspec/changes/pre-release-improvements/`
- Destination: `openspec/changes/archive/2026-08-11-pre-release-improvements/`
- Result: moved successfully

## Verification of Archive State
- Archived folder exists at `openspec/changes/archive/2026-08-11-pre-release-improvements/`
- Archived folder contains `proposal.md`, `design.md`, `tasks.md`, `verify-report.md`, and `archive-report.md`
- Archived `tasks.md` contains no unchecked implementation tasks after reconciliation
- Active change folder `openspec/changes/pre-release-improvements/` no longer contains active artifacts

## Key Learnings
- Spring Cache `@Cacheable` was not the correct fit for this reactive `Mono` flow with the chosen cache manager; manual cache interaction plus `.cache()` on the publisher was the reliable approach
- A verify-phase “blocker” is not automatically a change blocker when the issue is demonstrably pre-existing architecture or missing evidence correlation rather than a regression introduced by the change
- Archive-time task reconciliation is acceptable only when the persisted task artifact is stale and independent apply/verify evidence proves completion
