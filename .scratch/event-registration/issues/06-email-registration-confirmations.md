# 06: Email Registration confirmations

**What to build:** After a successful Registration, the Attendee receives an email confirming it and, for an online Event, providing the join URL.

**Blocked by:** 02: Register an Attendee and review Registrations.

**Status:** ready-for-agent

- [ ] A successful Registration triggers one confirmation email to the Attendee's provided email address.
- [ ] The email identifies the Event and confirms the Attendee's Registration.
- [ ] For an online Event, the email includes the join URL; public Event responses continue to omit it.
- [ ] Rejected duplicate requests do not send another confirmation email.
- [ ] The email provider is configured outside the source code; tests capture outgoing messages with a fake adapter rather than sending real email.
- [ ] This ticket adds backend API and email-delivery behavior only; it creates no frontend or UI.
