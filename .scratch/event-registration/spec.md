# Event Registration

**Status:** ready-for-agent

> Ready for ticket decomposition. API route/media details and deployment configuration remain implementation choices; the product defaults recorded below are approved.

## Problem Statement

The project has no way for an Organizer to publish an Event or for an Attendee to register for one. People cannot discover the project’s events, sign up to attend, or know whether an Event or Registration has been cancelled.

## Solution

Provide a backend API where a trusted Organizer can create, publish, edit, cancel, and review Registrations for public Events. Attendee-facing clients can retrieve Events and make a free, immediate, guest Registration for an Event as a whole. Successful Registrations trigger a confirmation email. This project does not build those clients or any other frontend.

An Event is one occurrence with one overall start/end range, possibly spanning multiple days. It is either in-person or online; hybrid Events and separately registrable sessions are not included. Registration is for one Attendee, is confirmed immediately, and has no capacity limit or waitlist.

## User Stories

1. As an Attendee, I want to retrieve a public list of Events through the API, so that a client can show me gatherings I may want to attend.
2. As an Attendee, I want the API to return an Event’s attendance mode and overall date/time range, so that a client can help me decide whether I can attend.
3. As an Attendee, I want to retrieve an Event’s details, so that a client can present where it takes place or that it is online.
4. As an Attendee, I want to create a Registration for myself through the API, so that I can attend without creating an account or selecting sessions.
5. As an Attendee, I want a successful Registration request to return an immediately confirmed Registration, so that the client can tell me I am registered.
6. As an Attendee, I want to provide my full name and email address in my Registration request, so that the Organizer can identify my Registration.
7. As an Attendee, I want a second active Registration from the same email address for the same Event to be rejected, so that duplicate requests do not inflate the Registration list.
8. As an Attendee, I want to cancel my Registration through the API while keeping its history, so that my plans can change without erasing the record.
9. As an Attendee, I want to register again after cancelling my earlier Registration, so that I can attend if my plans change back.
10. As an Organizer, I want to create and publish an Event through the protected API immediately, so that it becomes available in the public Event listing without a draft or approval workflow.
11. As an Organizer, I want to edit a published Event through the API, so that I can correct or update its details.
12. As an Organizer, I want to cancel a published Event through the API without deleting it, so that its history and Registrations remain available.
13. As an Organizer, I want active Registrations to be marked cancelled when their Event is cancelled, so that API clients see that the Event will not take place.
14. As an Organizer, I want to retrieve an Event’s Registration list through the protected API, so that I can see who has signed up.
15. As an Organizer, I want Events and Registrations to persist in PostgreSQL, so that they remain available after the application restarts.
16. As an Organizer, I want to create Events that are either in-person or online, so that API clients can present the two agreed attendance modes.
17. As an Attendee, I want to register for a multi-day Event with one Registration, so that I do not need to sign up separately for each day or session.
18. As an Organizer, I want repeated meetups to be represented as separate Events, so that each Event has one overall occurrence and time range.

19. As an Organizer, I want to submit a title, start/end time, and a venue/address or online join URL, with an optional description, so that Attendee clients can present the details needed to decide and attend.
20. As an Attendee, I want Event responses to include an explicit time zone, so that a client can present the schedule correctly.
21. As an Organizer, I want to authenticate with a single configured Organizer credential, so that only the trusted Organizer can manage Events and view Registrations.
22. As an Attendee, I want a confirmation email after registering, so that I have a durable record of my Registration.
23. As an online Attendee, I want the join URL returned only after I register, so that public Event responses do not disclose attendance details to people who have not registered.

## Implementation Decisions

### Confirmed decisions

- Expose a backend HTTP API for public Event retrieval and Attendee Registration, plus protected Organizer operations for Event management and Registration review. Frontend clients are outside this project’s scope.
- Organize the feature around an `events` feature boundary with inbound, application, domain, and outbound responsibilities. Keep framework and database types outside the domain model.
- Use PostgreSQL as the durable source of truth, with the project’s existing jOOQ, Flyway, and PostgreSQL Testcontainers support.
- Represent each Event as one occurrence. An Event has one overall start/end range, can be multi-day, and has one attendance mode: in-person or online.
- Publish an Event immediately when the Organizer creates it. Organizers can edit or cancel published Events; cancellation preserves the Event record and its Registration history.
- A Registration is for one Attendee, covers the whole Event, is free, and is confirmed immediately. There are no capacity limits, waitlists, payments, or ticketing.
- Attendees do not need accounts. The Organizer area is protected, and v1 has one trusted Organizer.
- Event requests include a title, optional description, overall start/end time, explicit time zone, attendance mode, and mode-specific venue/address or online join URL.
- The single Organizer authenticates with a credential configured outside the source code. Attendee names and email addresses are only available through the protected Organizer API.
- Public Event responses omit the online join URL. A successful Registration response and its confirmation email include the URL for an online Event.
- Send a confirmation email after each successful Registration; the email provider is configured outside the source code.
- Allow at most one active Registration per normalized email address per Event. A cancelled Registration remains in history; the same email can create a new Registration after cancellation.
- When an Organizer cancels an Event, preserve the Event and all Registrations, and mark each still-active Registration as cancelled because its Event was cancelled. A previously attendee-cancelled Registration keeps that reason.
- Guest Registration cancellation requires a secure per-Registration credential issued to the Attendee; Registration IDs alone do not authorize cancellation.

The accepted state transitions from the logic prototype are:

```text
Event:       published → cancelled
Registration: confirmed → attendee-cancelled
Registration: confirmed → event-cancelled (when its Event is cancelled)
```

The prototype also demonstrated that a new Registration may be made for the same email after its prior Registration has been attendee-cancelled. Source: `prototype/event-registration-logic` branch, commit `378ca51`.

### Testing seam

- Exercise the feature through one Spring MVC HTTP API boundary, backed by a real PostgreSQL Testcontainer. This tests public and protected API behavior across the inbound, application, domain, and persistence responsibilities without duplicating behavior in separate controller and repository tests.
- Assert observable HTTP status, response data, access control, and the resulting persisted Event and Registration state. Capture outgoing email with a fake adapter rather than sending real messages in tests.
- The project’s prior test is a Spring context-load test that imports Testcontainers configuration. There are no existing behavioral feature tests; the HTTP/PostgreSQL seam is new.
- A good test checks what an Attendee or Organizer can observe, not private class structure or individual implementation methods.

## Out of Scope

- Hybrid Events, recurring Event series, separately registrable sessions or talks, and multi-day per-session schedules.
- Group Registration, Attendee accounts, and public self-service Organizer account creation or multiple Organizer roles.
- Capacity limits, waitlists, payments, tickets, check-in, attendance tracking, and calendar synchronization.
- All frontend work, including browser pages, HTML forms, UI components, and client applications.

## Further Notes

- API resource shapes, route conventions, media types, and the exact representation of the cancellation credential are implementation decisions; the project has Spring MVC but no existing API conventions.
- The registration closing time, whether Event edits/cancellations send notifications, and whether cancelled Events remain in the public listing have not been decided.
- The production database connection and email provider are not configured yet. The build includes both PostgreSQL and MongoDB test support; the feature will use PostgreSQL.
- The prototype is captured on `prototype/event-registration-logic`. Add a pointer to it from the relevant implementation ticket when tickets are published.
