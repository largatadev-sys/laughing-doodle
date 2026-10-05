package com.largatadev.timesheet.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Worklog's answer for one Event in a batch, in request order. {@code eventId} echoes what was
 * sent (null when it was not a string at all); {@code reason} is a dotted field key and is
 * present only on {@code rejected}.
 */
public record EventVerdict(
		String eventId,
		String status,
		@JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

	public static final String ACCEPTED = "accepted";
	public static final String DUPLICATE = "duplicate";
	public static final String REJECTED = "rejected";

	public static EventVerdict accepted(String eventId) {
		return new EventVerdict(eventId, ACCEPTED, null);
	}

	public static EventVerdict duplicate(String eventId) {
		return new EventVerdict(eventId, DUPLICATE, null);
	}

	public static EventVerdict rejected(String eventId, String reason) {
		return new EventVerdict(eventId, REJECTED, reason);
	}
}
