# Actionable Problem Details for HTTP 400 Responses

Status: resolved

## Problem Statement

API clients receive generic HTTP 400 Problem Details for invalid requests, such as `detail: "Failed to read request"`. These responses do not identify invalid input or explain how to correct it. Request-binding and validation errors across MVC endpoints are affected.

## Solution

Return actionable, consistent Problem Details for MVC-generated 400 responses. Preserve standard metadata and add an `errors` extension containing request fields and human-readable messages where they can be identified. For malformed JSON with no identifiable field, return a safe body-level error. Use the existing full-application HTTP API test seam end to end.

## User Stories

1. As an Organizer API client, I want an invalid Event property identified in the 400 response, so that I can correct the request.
2. As an Organizer API client, I want a missing required Event property named in the response, so that I know what to provide.
3. As an Organizer API client, I want invalid typed values, such as timestamps, attendance modes, or URLs, explained by field, so that I can submit valid values.
4. As an Organizer API client, I want Event validation failures to include the relevant constraint message, so that I understand the validation rule.
5. As an Organizer API client, I want all available validation errors returned together, so that I can fix multiple problems in one iteration.
6. As an API client, I want malformed JSON reported as a body-level problem when no field can be identified, so that the response is more useful than a generic read failure.
7. As a public API client, I want invalid page-size values identified in the response, so that I can request a valid Event-list page.
8. As a public API client, I want malformed Event-list cursors explained as request errors, so that I can correct or remove the cursor.
9. As a public API client, I want an invalid Event identifier identified as the source of a 400, so that I can correct the path value.
10. As an API client, I want the same error structure across MVC endpoints, so that my client can handle errors consistently.
11. As an API client, I want standard Problem Details metadata and the problem JSON content type preserved, so that I can reliably parse errors.
12. As an API client, I want field names to correspond to external JSON properties, query parameters, or path variables, so that I can map errors to the right input.
13. As an API client, I want concise, safe messages rather than exception details or raw submitted values, so that responses are actionable without exposing internals.
14. As an Organizer API client, I want existing Event domain-rejection errors to retain their structured errors, so that domain-rule failures remain actionable.
15. As an API client, I want valid requests and non-400 behavior unchanged, so that improving errors does not alter successful API use.
16. As an API client, I want consistent MVC 400 handling regardless of the controller, so that error behavior does not depend on the endpoint.

## Implementation Decisions

- Add global inbound MVC error handling based on `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler` and registered through REST controller advice.
- Give the advice precedence over Spring Boot's default Problem Details handling.
- Handle unreadable bodies, Bean Validation failures, handler-method validation failures, and request parameter/path binding or conversion failures.
- Retain the enabled Problem Details configuration and standard metadata. Use the existing `errors` extension shape: entries contain `field` and `message`.
- Use external request names when available and `body` for syntax-level failures without an identifiable field.
- Return concise messages for known input failures without stack traces, exception class names, framework diagnostics, or raw submitted values.
- Return all available validation errors; deserialization failures need only describe the failure actually available.
- Preserve existing Event domain rejections and successful responses. No schema or domain-rule changes.

## Testing Decisions

- Test observable HTTP behavior through the existing full-application Event API integration tests; no new lower-level seam or exception-handler unit tests.
- Assert status, content type, Problem Details metadata, and actionable error fields/messages without coupling to handler implementation.
- Cover malformed JSON, empty bodies, missing/null and invalid body properties, multiple validation errors, custom Bean Validation messages, invalid query parameters/cursors, and invalid path identifiers.
- Replace shared assertions that errors are absent with structured error expectations once all supported MVC cases are covered.
- Preserve domain rejection and successful-request coverage. Keep security behavior unchanged.

## Out of Scope

- Security/servlet-filter 400s, including firewall rejections before MVC handling.
- Changes to accepted Event inputs, domain rules, authentication/authorization, non-400 behavior, or successful response contracts.

## Further Notes

- The glossary terms Event and Organizer apply; no relevant ADRs exist.
- The user explicitly confirmed use of ResponseEntityExceptionHandler in the conversation.
- Implementation tickets form a dependency graph in the adjacent issues directory.

### Completion — 2026-11-09

- Implemented on integration branch `feature/actionable-problem-details`; both tickets are resolved.
- Standards review found no documented violations and one optional duplication finding; the duplication and all three Spec review findings were addressed in `f05045b`, merged as `8edb86c`.
- Final integration verification: `./gradlew test` passed **105 tests**, including **79 HTTP cases**, with zero failures, errors, or skips. The complete diff passes `git diff 71827a8...HEAD --check`.
- Removed all three implementation worktrees after verifying clean status and merged ancestry; unrelated worktrees were left untouched.
- Security-filter 400s remain out of scope. The upstream cross-parameter-only validation dispatch limitation is documented in ticket 02; all validation errors available on MVC-generated 400 exceptions are included.
