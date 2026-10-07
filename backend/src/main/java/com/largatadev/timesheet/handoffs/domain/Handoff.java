package com.largatadev.timesheet.handoffs.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A Handoff as stored: who made it, when, the Reports it covers in the order its text numbers
 * them, and the text exactly as the client built it. Never edited, never deleted.
 */
public record Handoff(UUID id, long createdBy, Instant createdAt, String text, List<UUID> reportIds) {

	public Handoff {
		reportIds = List.copyOf(reportIds);
	}
}
