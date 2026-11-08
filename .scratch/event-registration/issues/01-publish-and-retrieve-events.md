# 01: Publish and retrieve Events through the API

**What to build:** A trusted Organizer can publish an Event, and public API clients can discover and retrieve it.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The Organizer API is protected by the single configured Organizer credential; there is no public Organizer sign-up.
- [ ] An Organizer can create an Event with a title, optional description, start/end time, explicit time zone, and either an in-person venue/address or an online join URL.
- [ ] An Event has exactly one attendance mode: in-person or online; hybrid Events are rejected.
- [ ] A newly created Event is published immediately and is available through public list and detail API operations.
- [ ] Public Event responses include the Event's attendance mode and time zone, but omit the online join URL and Attendee names/emails.
- [ ] Event details persist in PostgreSQL and remain available after an application restart.
- [ ] Invalid Event details are rejected through the API with a clear client-visible result.
- [ ] This ticket adds backend API behavior only; it creates no frontend or UI.
