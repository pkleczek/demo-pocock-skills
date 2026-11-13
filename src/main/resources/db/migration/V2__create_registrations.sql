CREATE TABLE registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('CONFIRMED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX registrations_one_active_email_per_event
    ON registrations (event_id, email) WHERE status = 'CONFIRMED';
