-- Handoffs (Story 28): a Member's frozen record of a set of Reports passed on for fixing —
-- who, when, which Reports in which order, and the exact text handed over. Schema signed off
-- by the developer, 2026-10-07. A Handoff is never edited or deleted: there is no route for
-- either, and nothing here alters an existing table. Creating one never changes a Report.
CREATE TABLE handoffs (
    -- Server-minted: a Handoff is authored here, so there is no foreign idempotency key.
    id         UUID        PRIMARY KEY,
    -- A worklog User, always the caller's JWT identity — never a field of the request.
    created_by BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    -- Client-built and stored exactly as given (spec, 2026-10-07): never trimmed, regenerated
    -- or compared with the Reports. The 1,000,000-character cap is enforced in Java only —
    -- Java counts UTF-16 units and Postgres counts code points, so a DB cap could disagree.
    text       TEXT        NOT NULL CHECK (length(text) > 0)
);

-- The Handoffs list reads newest first.
CREATE INDEX ix_handoffs_created_at ON handoffs (created_at DESC);

-- One row per Report per Handoff. position is 0-based: the order the text numbers them in.
CREATE TABLE handoff_reports (
    handoff_id UUID    NOT NULL REFERENCES handoffs (id),
    report_id  UUID    NOT NULL REFERENCES reports (id),
    position   INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (handoff_id, report_id),
    UNIQUE (handoff_id, position)
);
