package com.largatadev.timesheet.dashboard.dto;

import java.util.List;

/**
 * How many of a counter were created and deleted in each bucket of the viewer's calendar,
 * oldest first, with empty buckets present as zeros. Empty {@code points} means the counter
 * has never had a {@code .created} or {@code .deleted} Event — not an error.
 */
public record DashboardSeries(String counter, String bucket, String zone, List<Point> points) {

	public record Point(String start, long created, long deleted) {
	}
}
