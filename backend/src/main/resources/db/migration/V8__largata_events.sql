-- Largata usage Events (Epic 4, ADR-014). Pushed server-to-server through the intake secret
-- that already guards /api/intake/**; stored as an opaque, append-only log and counted on read.
-- Nothing references users and nothing here has an owner, so INV-2 is untouched. No row is
-- ever updated or deleted: there is no route for either, and no pruning (retention is an
-- ADR-014 invalidator, not a feature).
CREATE TABLE largata_events (
    -- Client-minted by Largata: the primary key IS the idempotency key, so a delivery retry
    -- collides on the PK (INSERT ... ON CONFLICT DO NOTHING) and answers "duplicate".
    id          UUID          PRIMARY KEY,
    -- Opaque to worklog. Only the reserved kind 'snapshot' and the '.created' / '.deleted'
    -- suffixes are interpreted; every other kind is counted per period and nothing more.
    kind        VARCHAR(100)  NOT NULL,
    -- The Traveler who did this (Largata's Traveler UUID, the same identity reporter_uid
    -- carries on a Report) — null when no Traveler did it (e.g. a Snapshot).
    subject     VARCHAR(200),
    -- occurred_at is Largata's clock (the act); received_at is arrival here. Store-and-forward
    -- means they legitimately differ; every count uses occurred_at.
    occurred_at TIMESTAMPTZ   NOT NULL,
    received_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Only on kind = 'snapshot': counter -> non-negative integer. JSON, not JSONB, on purpose:
    -- JSON keeps the text as sent, so the Dashboard can list counters in the order Largata's
    -- Snapshot lists them (JSONB re-sorts keys by length). Nothing ever queries inside it by
    -- key, so JSONB's indexing buys nothing here.
    totals      JSON
);

CREATE INDEX ix_largata_events_kind_occurred_at ON largata_events (kind, occurred_at);
CREATE INDEX ix_largata_events_occurred_at ON largata_events (occurred_at);
-- The latest-Snapshot lookup (freshness, and the baseline every total re-bases on).
CREATE INDEX ix_largata_events_latest_snapshot ON largata_events (occurred_at DESC)
    WHERE kind = 'snapshot';
