package com.largatadev.timesheet.handoffs.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the Handoffs list: when, who and how many. Deliberately never the text or the
 * Report ids — those are what opening the Handoff is for.
 */
public record HandoffSummaryResponse(
		UUID id,
		OffsetDateTime createdAt,
		Long createdBy,
		String createdByName,
		int reportCount) {
}
