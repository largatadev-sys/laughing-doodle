package com.largatadev.timesheet.dashboard.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An Event that passed the envelope checks and is ready to store. {@code totalsJson} is the
 * Snapshot's counter map re-serialised in the order Largata sent it; null on every other kind.
 */
public record LargataEvent(UUID id, String kind, String subject, Instant occurredAt, String totalsJson) {
}
