# 01: Publish and retrieve Events through the API

**What to build:** A trusted Organizer can publish an Event, and public API clients can discover and retrieve it.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent
**Resolution:** completed (2026-11-09)

- [x] The Organizer API is protected by the single configured Organizer credential; there is no public Organizer sign-up.
- [x] An Organizer can create an Event with a title, optional description, start/end time, explicit time zone, and either an in-person venue/address or an online join URL.
- [x] An Event has exactly one attendance mode: in-person or online; hybrid Events are rejected.
- [x] A newly created Event is published immediately and is available through public list and detail API operations.
- [x] Public Event responses include the Event's attendance mode and time zone, but omit the online join URL and Attendee names/emails.
- [x] Event details persist in PostgreSQL and remain available after an application restart.
- [x] Invalid Event details are rejected through the API with a clear client-visible result.
- [x] This ticket adds backend API behavior only; it creates no frontend or UI.

## Comments

### 2026-11-09 — Completion verification

- Existing implementation satisfies the API, authentication, validation, and PostgreSQL requirements; no production changes were necessary to close this ticket.
- Evidence: `src/test/kotlin/com/example/demo/events/EventHttpApiTests.kt` exercises publication, public list/detail retrieval, credential enforcement, attendance-mode validation, and safe public responses through the HTTP API against real PostgreSQL. `UnconfiguredOrganizerCredentialTests.kt` verifies that missing configuration never grants Organizer access.
- Durability is provided by the PostgreSQL-backed `JooqEventRepository` and Flyway-managed `events` table. The application does not rely on in-memory Event storage; a separate application-restart test adds no useful coverage here.
- Validation: `./gradlew test --rerun-tasks` passes. Full suite: 108 tests, no failures or skipped tests.
