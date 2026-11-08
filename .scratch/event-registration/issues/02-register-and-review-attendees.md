# 02: Register an Attendee and review Registrations

**What to build:** An Attendee can make a guest Registration for a public Event, and the Organizer can retrieve that Event's private Registration list.

**Blocked by:** 01: Publish and retrieve Events through the API.

**Status:** ready-for-agent

- [ ] An Attendee can submit their full name and email address to register themself for a published Event.
- [ ] A successful request creates one free, immediately confirmed Registration and persists it in PostgreSQL.
- [ ] There is at most one active Registration per normalized email address per Event; duplicate requests do not create another Registration.
- [ ] The authenticated Organizer can retrieve the Event's Registrations, including Attendee names and email addresses; public API operations do not reveal those details.
- [ ] For an online Event, the successful Registration response includes the join URL; public Event responses do not.
- [ ] Events have no capacity limit or waitlist, and Registration does not involve payment or ticketing.
- [ ] This ticket adds backend API behavior only; it creates no frontend or UI.
