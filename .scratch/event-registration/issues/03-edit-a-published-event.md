# 03: Edit a published Event

**What to build:** An Organizer can update a published Event through the API, and clients retrieving the Event receive its updated details.

**Blocked by:** 01: Publish and retrieve Events through the API.

**Status:** ready-for-agent

- [ ] The authenticated Organizer can edit a published Event's supported details.
- [ ] Public Event retrieval reflects the latest details while continuing to withhold the online join URL.
- [ ] Editing an Event does not alter its existing Registrations.
- [ ] Invalid edits are rejected through the API with a clear client-visible result.
- [ ] Changes persist in PostgreSQL.
- [ ] This ticket adds backend API behavior only; it creates no frontend or UI.
