CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    description TEXT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    attendance_mode VARCHAR(20) NOT NULL CHECK (attendance_mode IN ('IN_PERSON', 'ONLINE')),
    location TEXT,
    join_url TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PUBLISHED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT events_end_after_start CHECK (end_at > start_at),
    CONSTRAINT events_attendance_details CHECK (
        (attendance_mode = 'IN_PERSON' AND location IS NOT NULL AND length(btrim(location)) > 0 AND join_url IS NULL)
        OR
        (attendance_mode = 'ONLINE' AND location IS NULL AND join_url IS NOT NULL AND length(btrim(join_url)) > 0)
    )
);
