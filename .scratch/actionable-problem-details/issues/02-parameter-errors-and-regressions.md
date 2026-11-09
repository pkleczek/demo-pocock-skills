# 02: Consistent parameter/path errors and end-to-end regressions

Status: resolved
Blocked by: 01

Spec: ../spec.md

## What to build

Extend the global advice from ticket 01 to handler-method validation and query/path conversion failures. Complete the shared HTTP contract assertions and regression coverage across both Event endpoints.

## Acceptance Criteria

- [x] Out-of-range page sizes expose the limit parameter and constraint message.
- [x] Invalid and overflowing integer query values expose limit with safe expected-type guidance.
- [x] Invalid cursors expose cursor with safe correction guidance, without encoded/decoded input or conversion internals.
- [x] Invalid Event path identifiers expose id with UUID guidance.
- [x] Applicable MVC binding and missing-parameter failures use external names and the same errors extension; unknown errors get a safe fallback.
- [x] Shared invalid-request assertions require actionable structured errors rather than their absence, and metadata/content-type remain correct.
- [x] Existing domain rejections, successful requests, and firewall/security behavior still pass the full test suite.

## Comments

Reuse only the full-application HTTP API seam. Preserve non-400 responses and avoid broad catch-all exception handling that could convert server defects into client errors.

### 2026-11-09 — Implementation started

- Claimed on `work/problem-details-parameters` in its dedicated worktree.
- Ancestry verified before edits: HEAD and `feature/actionable-problem-details` both at `4acc152`, with a clean worktree and zero commits ahead/behind. Ticket 01 is resolved on integration.
- Approved seam: `EventHttpApiTests` full-application HTTP requests with real PostgreSQL Testcontainers. No handler unit tests or lower seams.
- Will leave this ticket unresolved until integration merge verification.

### 2026-11-09 — Implementation and local verification

- Extended the existing highest-precedence `ResponseEntityExceptionHandler` advice for query/path conversion, missing query parameters, request binding, and handler-method validation. No catch-all exception handler was added.
- Page-size errors preserve constraint messages; invalid/overflowing integers get integer guidance; cursor errors explain using a returned cursor or omitting it; malformed and whitespace-only Event identifiers get UUID guidance. Conversion diagnostics and submitted values are not copied into error messages.
- Missing request names and validation aliases use their external names. Unknown conversion types get `has an invalid value`; MVC binding errors without structured field information get a safe `request` fallback. Query-object binding failures no longer reuse Spring's unsafe default conversion message.
- Method validation retains all available parameter/field failures, including simultaneous aliased query/path errors and simultaneous body/query errors. Server-side return-validation and path-mapping defects retain 500 responses.
- Shared invalid-request assertions now always require a nonempty, exact `errors` list, standard status/title/instance/type semantics, problem JSON, a useful detail, and no conversion/framework diagnostics. The no-Event-created assertion now uses the public Event-list HTTP response instead of a database query.
- All new tests remain in `EventHttpApiTests` at the approved full-application HTTP seam. Nested test-only routes trigger real MVC binding/validation paths that the current optional Event-list parameters cannot reach; no exception objects are constructed, collaborators mocked, or handler internals invoked.

Actual incremental red/green evidence: each row used `./gradlew test --tests '*EventHttpApiTests.<test name>'` before and after its minimal implementation (the UUID/cursor run also selected the malformed-cursor-content regression).

| Slice / test name | Red observed before implementation | Green observed after implementation |
| --- | --- | --- |
| `Bean Validation enforces the public event list page size` | 3 failures: missing `errors` | All 3 cases passed |
| `Spring rejects page sizes that cannot bind to an integer` | 2 failures: missing `errors` | Both cases passed, including overflow and raw-value exclusion |
| `public event list rejects an invalid cursor` | 1 failure: generic invalid-value fallback instead of cursor correction guidance | Passed |
| `invalid Event identifiers explain the expected UUID path value` (initial malformed-ID case) | 1 failure: generic invalid-value fallback instead of UUID guidance; 3 malformed-cursor-content cases already passed | UUID case plus all 3 cursor-content cases passed |
| `missing required query parameters use their external request names` | 1 failure: missing `errors` | Passed |
| `MVC query-object binding returns safe field guidance instead of conversion diagnostics` | 1 failure: default message exposed `java.lang.String`, the required JVM type, and `private-invalid-number` | Passed with integer guidance and no submitted value |
| `method validation returns all query and path errors using external aliases` | 1 failure: path field was `internalSequence`, not external `sequence` | Passed with both external names/messages |
| `method validation preserves body field errors alongside query errors` | 1 failure: body failure was labelled `body`, not `title` | Passed with both body/query errors |
| `missing required headers return structured binding errors with external names` | 1 failure: missing `errors` | Passed |
| `invalid Event identifiers explain the expected UUID path value` (added whitespace case) | 1 failure out of 2: whitespace conversion yields a distinct missing-after-conversion exception without `errors` | Both malformed/whitespace cases passed |

- Regression command `./gradlew test --tests '*EventHttpApiTests'`: **73 tests passed, 0 failures, 0 errors, 0 skipped**. This includes unknown conversion/binding fallbacks, 405 with its Allow header, 415, 500 defects, domain rejections, successful requests, authentication, and actual-server firewall checks.
- Final local command `./gradlew test`: **BUILD SUCCESSFUL**, test task executed; parsed all six XML reports under `build/test-results/test/`: **99 tests passed, 0 failures, 0 errors, 0 skipped**, including **73 HTTP cases**.
- All runs used real PostgreSQL Testcontainers through the existing socket configuration, with no Docker CLI, environment overrides, dependency changes, or build changes. The first red run also generated this worktree's jOOQ sources through real Testcontainers/Flyway. `git diff --check` passed.
- Before reporting, ran `git merge feature/actionable-problem-details`: **Already up to date**; latest integration tip remains `4acc1524ebaf1060382cb63ff35c089afff950dd` and is an ancestor of this branch.
- Local acceptance coverage is complete, but status remains `claimed` and acceptance checkboxes remain unchecked pending the coordinator's merge and test verification on integration. No PR was created; no worktree cleanup was performed.

### 2026-11-09 — Review follow-up: SPEC1, SPEC2, SPEC3, and response duplication

- Started with a clean worktree at the requested integration tip `b0604583e092abeb9dcb6113c8483fb9aca9e371` on `work/problem-details-review`; confirmed that tip is an ancestor. No unknown changes were discarded and no agents were spawned.
- **SPEC1:** Missing matrix variables now use `MissingMatrixVariableException.variableName`, returning `revision` / `is required` from the existing aliased probe. Retained the generic binding fallback and exercised it separately through an actual missing request attribute, whose exception has no structured field metadata.
- **SPEC2 (also ticket 01):** Both validation dispatch paths translate request-body field paths using the application's configured Jackson deserialization metadata. Test-only DTOs exercise `@JsonProperty` aliases, nested indexed paths, and a configured snake-case naming strategy. Translation is limited to `@RequestBody`; HTTP regressions verify both query-object validation dispatch paths retain MVC property names and query conversion ignores JSON aliases.
- **SPEC3:** Appended every available cross-parameter validation result as a `request` error with its constraint message. A real cross-parameter constraint is invoked through HTTP together with an invalid query parameter; the response retains both errors. Existing 500 return-validation/path-mapping behavior remains unchanged.
- **Standards:** Extracted only `handleWithErrors`, which attaches errors to an existing `ProblemDetail` and calls `handleExceptionInternal`. Per-exception mapping, metadata construction, and non-400 guards remain visible in the overrides.
- Used only the approved full-application `EventHttpApiTests` HTTP seam, real MVC validation/binding, and real PostgreSQL Testcontainers. No handler unit tests, manually constructed exceptions, mocks, dependency changes, or build/environment changes were introduced.

Incremental red/green evidence (each red run preceded the corresponding production change):

| Slice / HTTP test | Red observed | Green observed |
| --- | --- | --- |
| `missing matrix variables use their external request names` | 1 failure: generic `request` fallback instead of `revision` / `is required` | Targeted test passed |
| `body validation uses external JSON aliases and configured nested property names` | 1 failure: `internalTitle` and `internalDetails[0].displayTitle` instead of external JSON names; actual `MethodArgumentNotValidException` dispatch | Targeted test passed with `event-title` and `event-details[0].display_title` |
| `method validation combines external JSON field paths with query parameter names` | 1 failure: body fields still used internal paths beside `page-size`; actual `HandlerMethodValidationException` dispatch | Both JSON-name tests passed with external body paths and unchanged query alias |
| `method validation includes cross-parameter constraints alongside parameter errors` | 1 failure: only `page-size` returned, cross-parameter message omitted | Targeted test and both existing server-error cases passed |

- Regression run before the response-helper cleanup: `./gradlew test --tests '*EventHttpApiTests'` — **79 HTTP tests passed**. Full suite after cleanup: `./gradlew test` — **BUILD SUCCESSFUL**, test task executed; all six XML reports total **105 tests, 0 failures, 0 errors, 0 skipped**, including **79 HTTP tests** (previous integration baseline: 99 / 73). `git diff --check` passed.
- Latest integration check: `git merge feature/actionable-problem-details` returned **Already up to date**; integration remains `b0604583e092abeb9dcb6113c8483fb9aca9e371`.
- **Upstream caveat, not a blocker for the MVC-generated-400 scope:** an additional exploratory cross-parameter-only case returned 200 when the query parameter was valid. Resolved Spring 7.0.10 source confirms `MethodValidationResult.hasErrors()` checks only parameter results, so MVC never dispatches an exception for that case. The retained HTTP regression exercises real cross-parameter errors available on an actual MVC 400 exception. No speculative framework workaround was added and non-400 behavior is unchanged. The exploratory failure is preserved in `review-http-before-cleanup.log`.
- Red/green logs and the final suite log are under the approved temporary directory: `review-red-matrix.log` / `review-green-matrix.log`, `review-red-body-names.log` / `review-green-body-names.log`, `review-red-method-names.log` / `review-green-method-names.log`, `review-red-cross-parameter.log` / `review-green-cross-parameter.log`, `review-http-green.log`, and `review-full-test.log`.
- Existing resolved ticket states and acceptance checkboxes remain unchanged. No new ticket is needed for these review fixes; integration merge/verification remains with the coordinator.

## Answer

### 2026-11-09 — Integration verification

- Verified `/Users/pkleczek/IdeaProjects/demo` was clean on `feature/actionable-problem-details` before merging; no existing edits were discarded.
- Merged `work/problem-details-parameters` at `49b41c0da2a56d0854ffc5e345efa8186d2afb97` with `--no-ff`, producing merge commit `1ff90e49e4d0790dc0ee7b3d4b0eab50f77dfa39` (`Merge ticket 02: actionable MVC parameter errors and regression coverage`). No conflicts occurred.
- Ran `./gradlew test` on the integration branch: **BUILD SUCCESSFUL**, with the test task executed.
- Parsed all six `build/test-results/test/TEST-*.xml` reports: **99 tests passed, 0 failures, 0 errors, 0 skipped**, including **73 Event HTTP API cases**. The full suite verifies the parameter/path errors, shared Problem Details contract, domain rejections, successful requests, and security regressions.
- `git diff --check 4acc152..HEAD` passed, and the integration worktree was clean after testing, before this tracker-only update. All acceptance criteria are complete; ticket 02 is resolved.
- No additional code or spec changes were made during integration. Review and worktree cleanup remain with the coordinator.

### 2026-11-09 — Reviewed implementation verified

- Merged all review corrections from `f05045be143eaf4f3300947084b7dccfc00e49be` as `8edb86cdff43bd13df1a3716b81ed792ee34f23e`, without conflicts.
- Integration `./gradlew test` executed successfully: **105 tests passed**, including **79 HTTP cases**; no failures, errors, or skips. Final complete-diff whitespace check passed.
- Reviewed the corrections to external field mapping, cross-parameter errors, and response completion. All Standards/Spec findings are addressed; tickets remain resolved.
- All implementation worktrees were clean, fully merged, and removed. No unrelated worktrees or user changes were discarded.
