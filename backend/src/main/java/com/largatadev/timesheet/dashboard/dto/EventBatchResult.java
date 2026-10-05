package com.largatadev.timesheet.dashboard.dto;

import java.util.List;

/** The {@code 200} body of the Events intake: one verdict per Event, in request order. */
public record EventBatchResult(List<EventVerdict> results) {
}
