-- Screenshot bytes live in Postgres, not object storage: at a 4-person team's feedback volume
-- this avoids standing up a bucket, and reports back up with the database as one unit. Storing
-- them here (rather than hotlinking Largata) is also what keeps the inbox readable when Largata
-- is down or changes. Revisit only if volume embarrasses it (spec, "Schema").
CREATE TABLE report_screenshots (
    report_id    UUID        NOT NULL REFERENCES reports (id),
    -- 0..2 — Largata sends at most 3 parts, in the order the reporter attached them.
    ordinal      SMALLINT    NOT NULL CHECK (ordinal BETWEEN 0 AND 2),
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    bytes        BYTEA       NOT NULL,
    PRIMARY KEY (report_id, ordinal)
);
