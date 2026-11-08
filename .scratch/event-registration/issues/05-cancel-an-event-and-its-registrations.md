# 05: Cancel an Event and its Registrations

**What to build:** An Organizer can cancel a published Event, and API clients can see that the Event and its active Registrations are cancelled without losing their history.

**Blocked by:** 04: Cancel and re-register an Attendee.

**Status:** ready-for-agent

- [ ] The authenticated Organizer can cancel a published Event through the API.
- [ ] The Event remains recorded with a cancelled status, and new Registration attempts are rejected.
- [ ] Every active Registration becomes event-cancelled; a Registration already cancelled by its Attendee retains that status.
- [ ] Public Event detail responses report the cancelled status; whether cancelled Events remain in the public list is left to the API listing contract.
- [ ] The Organizer can retrieve the Event and its Registration history after cancellation.
- [ ] Cancellation state persists in PostgreSQL.
- [ ] Follow the validated Event/Registration transitions from the logic prototype: `prototype/event-registration-logic`, commit `378ca51`.
- [ ] This ticket adds backend API behavior only; it creates no frontend or UI.
