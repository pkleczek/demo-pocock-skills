# Registration API (ticket 02)

The API accepts and returns JSON. Organizer operations require
`Authorization: Bearer <ORGANIZER_API_KEY>` using the existing configured credential.

## Register an Attendee

`POST /api/events/{eventId}/registrations` is a guest operation; no account or
Organizer credential is required.

```json
{
  "fullName": "Ada Lovelace",
  "email": "ada@example.test"
}
```

A successful request returns `201 Created`:

```json
{
  "id": "8bde52f4-090a-4123-b69b-e92aecbe82af",
  "eventId": "3da67ac5-3bad-45f1-b602-526f7de2ef05",
  "status": "CONFIRMED",
  "joinUrl": "https://meet.example.test/private-room"
}
```

`joinUrl` is populated only for an online Event; it is null for an in-person
Event. Names and email addresses are omitted from guest responses. Public Event
list/detail responses continue to omit the join URL and Attendee details.

The full name is trimmed and must not be blank. Email addresses are trimmed,
lowercased using locale-independent rules, and validated as conventional ASCII
email addresses (unquoted dot-separated local parts and a dotted domain), with a
maximum length of 254 characters. Normalization does not remove dots or `+`
suffixes. The normalized address is stored and returned to the Organizer.

There is one active Registration per normalized email per Event, enforced in
PostgreSQL even for concurrent requests. Registrations cover the whole Event,
including multi-day Events. There is no capacity, payment, or ticket selection.

Rejections:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid UUID, malformed/missing request fields, blank name, or invalid email; structured `errors` describe invalid fields. |
| `404 Not Found` | The Event does not exist. |
| `409 Conflict` | An active Registration already exists, or the Event is cancelled. |

Domain rejections use `application/problem+json` and do not echo submitted
Attendee details or disclose the join URL.

## Review an Event's Registrations

`GET /api/organizer/events/{eventId}/registrations` requires the Organizer bearer
credential. A successful response is `200 OK`:

```json
{
  "items": [
    {
      "id": "8bde52f4-090a-4123-b69b-e92aecbe82af",
      "eventId": "3da67ac5-3bad-45f1-b602-526f7de2ef05",
      "fullName": "Ada Lovelace",
      "email": "ada@example.test",
      "status": "CONFIRMED"
    }
  ]
}
```

Results contain only that Event's Registrations, ordered by creation time and
identifier. An existing Event with no Registrations returns `{"items":[]}`;
an unknown Event returns `404`. Missing or invalid credentials return `401`.
There is no public Registration list or public lookup by Registration ID.

Cancellation credentials and cancellation/re-registration are ticket 04;
Event cancellation is ticket 05; confirmation email delivery is ticket 06.
This ticket does not implement those operations or any frontend.
