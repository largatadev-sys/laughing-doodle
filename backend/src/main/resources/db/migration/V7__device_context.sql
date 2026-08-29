-- Intake contract v1.2 (Story 21; spec amendment 2026-08-29). What the reporter was running
-- when they filed: OS (name and version as Largata formats it), browser (browser reports
-- only) and device model — each an opaque Largata-minted string under screen's rule:
-- optional forever, length-capped, never parsed or checked against a vocabulary here.
-- Captured on the reporter's device at report time; nothing back-fills, so every pre-v1.2
-- row stays null on these forever.
ALTER TABLE reports ADD COLUMN os VARCHAR(200);
ALTER TABLE reports ADD COLUMN browser VARCHAR(200);
ALTER TABLE reports ADD COLUMN device_model VARCHAR(200);
