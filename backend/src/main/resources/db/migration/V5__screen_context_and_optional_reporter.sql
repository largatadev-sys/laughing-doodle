-- Intake contract v1.1 (Story 19; spec amendment 2026-08-28; ADR-011). Largata's report
-- entry point becomes globally visible — signed-out screens included — so a Report may now
-- arrive with no reporter identity; and it carries which screen the reporter was on when
-- they opened the report flow, as an opaque Largata-minted string worklog never validates.
ALTER TABLE reports ALTER COLUMN reporter_name DROP NOT NULL;
ALTER TABLE reports ALTER COLUMN reporter_uid DROP NOT NULL;
ALTER TABLE reports ADD COLUMN screen VARCHAR(200);
