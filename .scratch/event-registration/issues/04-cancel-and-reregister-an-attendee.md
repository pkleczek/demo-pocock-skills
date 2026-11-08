# 04: Cancel and re-register an Attendee

**What to build:** An Attendee can securely cancel their own Registration through the API, retain its history, and register again if their plans change.

**Blocked by:** 02: Register an Attendee and review Registrations.

**Status:** ready-for-agent

- [ ] A successful Registration provides the Attendee with a non-guessable, per-Registration cancellation credential.
- [ ] The cancellation API requires that credential; knowing a Registration identifier or email address alone is insufficient to cancel it.
- [ ] Cancelling a confirmed Registration retains the record and changes its status to attendee-cancelled.
- [ ] After cancellation, the same email address can create a new active Registration for the Event.
- [ ] Cancellation and re-registration outcomes are visible through the API and persist in PostgreSQL.
- [ ] This ticket adds backend API behavior only; it creates no frontend or UI.
