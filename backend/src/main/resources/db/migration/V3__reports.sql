-- Incoming feedback from Largata users (Epic 3, ADR-010). Reports arrive server-to-server
-- through the shared-secret intake route; reporters are foreign to worklog (name/uid are
-- opaque data, never a users row), so nothing here touches auth or INV-2.
CREATE TABLE reports (
    -- Client-minted by Largata: the primary key IS the idempotency key, so a delivery retry
    -- collides on the PK instead of creating a duplicate row.
    id                UUID          PRIMARY KEY,
    type              VARCHAR(20)   NOT NULL CHECK (type IN ('problem', 'idea')),
    description       VARCHAR(2000) NOT NULL,
    reporter_name     VARCHAR(200)  NOT NULL,
    reporter_uid      VARCHAR(200)  NOT NULL,
    platform          VARCHAR(20)   NOT NULL CHECK (platform IN ('android', 'ios', 'web')),
    app_version       VARCHAR(50)   NOT NULL,
    -- submittedAt is the reporter's action (from the payload); received_at is arrival here.
    -- Store-and-forward relay means they legitimately differ; display/sort uses submitted_at.
    submitted_at      TIMESTAMPTZ   NOT NULL,
    received_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('new', 'discuss', 'in_progress', 'done', 'dismissed')),
    -- Null until the first triage; a Report has no owner, so this records who touched it last.
    status_changed_by BIGINT        REFERENCES users (id),
    status_changed_at TIMESTAMPTZ
);

CREATE INDEX ix_reports_submitted_at ON reports (submitted_at DESC);
CREATE INDEX ix_reports_status ON reports (status);
