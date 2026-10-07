package com.largatadev.timesheet.handoffs.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One Handoff in full: the summary fields (id, when, who, how many) plus the text byte for
 * byte and the Report ids in the order the text numbers them.
 */
public record HandoffResponse(
		UUID id,
		OffsetDateTime createdAt,
		Long createdBy,
		String createdByName,
		int reportCount,
		String text,
		List<UUID> reportIds) {
}
