-- Team notes on Reports (Story 20, ADR-012 — reverses the spec's original "no comments").
-- The LOG is append-only: there is no delete route and nothing here ever removes a row. Each
-- note's TEXT is editable by any Member, which is what edited_by/edited_at record — the
-- developer's explicit call, made safe by the stamp being visible rather than by a lock.
--
-- Both editor columns land in this one migration even though only the create path uses them
-- yet: the schema change was signed off once, and a second ALTER for the edit story would be
-- churn for no gain.
CREATE TABLE report_notes (
    -- Server-minted, unlike reports.id: a Note is authored here, so there is no foreign
    -- idempotency key to honour.
    id         UUID          PRIMARY KEY,
    report_id  UUID          NOT NULL REFERENCES reports (id),
    -- The author is a worklog User (the team talking to its future self) — never a reporter,
    -- who is foreign identity as data and has no row anywhere.
    author_id  BIGINT        NOT NULL REFERENCES users (id),
    body       VARCHAR(2000) NOT NULL CHECK (length(body) > 0),
    created_at TIMESTAMPTZ   NOT NULL,
    -- Null until someone edits; then whoever edited last, and when. Never a history — a
    -- changed decision is meant to be a new Note (spec amendment, 2026-08-29).
    edited_by  BIGINT        REFERENCES users (id),
    edited_at  TIMESTAMPTZ
);

-- The only read: every note for the reports in the inbox, oldest-first.
CREATE INDEX ix_report_notes_report ON report_notes (report_id, created_at);
