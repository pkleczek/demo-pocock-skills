# 02: Register an Attendee and review Registrations

**What to build:** An Attendee can make a guest Registration for a public Event, and the Organizer can retrieve that Event's private Registration list.

**Blocked by:** 01: Publish and retrieve Events through the API.

**Status:** ready-for-agent
**Resolution:** implemented; awaiting human review (2026-11-13)

- [x] An Attendee can submit their full name and email address to register themself for a published Event.
- [x] A successful request creates one free, immediately confirmed Registration and persists it in PostgreSQL.
- [x] There is at most one active Registration per normalized email address per Event; duplicate requests do not create another Registration.
- [x] The authenticated Organizer can retrieve the Event's Registrations, including Attendee names and email addresses; public API operations do not reveal those details.
- [x] For an online Event, the successful Registration response includes the join URL; public Event responses do not.
- [x] Events have no capacity limit or waitlist, and Registration does not involve payment or ticketing.
- [x] This ticket adds backend API behavior only; it creates no frontend or UI.

## Comments

### 2026-11-13 — Implementation

- Guest `POST /api/events/{eventId}/registrations` returns a confirmed Registration (`201`), with an online Event's join URL but no Attendee details. Duplicate active emails and cancelled Events return `409`; unknown Events return `404`.
- Protected `GET /api/organizer/events/{eventId}/registrations` retrieves persisted Attendee names, normalized email addresses, and Registration statuses, scoped to one Event.
- Email normalization trims and lowercases without provider-specific dot/plus rewriting. A PostgreSQL partial unique index enforces one confirmed Registration per Event/email under concurrent requests and leaves room for the cancellation history added in ticket 04.
- HTTP/PostgreSQL tests cover the successful flow, concurrency, privacy/authentication, Event scoping, invalid requests, and multi-day Event registration. API contract: `docs/event-registration-api.md`.
- Cancellation credentials and email delivery remain in tickets 04 and 06. Accepted logic prototype: `prototype/event-registration-logic`, commit `378ca51` (see the spec).
- Feature branch: `feature/register-and-review-attendees`; human review is the final phase.
- Final validation: `./gradlew test --rerun-tasks` passes with 138 tests, zero failures/errors/skips, including 25 Registration HTTP/PostgreSQL cases. Kotlin production/test compilation and jOOQ generation run as part of the build.
- An oversized dotted-email regression test caught regex stack exhaustion; validation now rejects addresses over 254 characters before evaluating their format.
- Code review against branch start `0ad520c`: Standards — 0 findings; Spec — 0 findings. The validation-hardening follow-up also passed both review axes. Awaiting the user's final branch review; nothing merged or pushed.
