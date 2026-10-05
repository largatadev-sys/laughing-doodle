package com.largatadev.timesheet.dashboard.dto;

import java.util.List;

/**
 * How many distinct Travelers were active in each bucket of the viewer's calendar, oldest
 * first, with empty buckets as zeros. Empty {@code points} means no Event has ever named a
 * Traveler.
 */
public record ActiveSeries(String bucket, String zone, List<Point> points) {

	public record Point(String start, long active) {
	}
}
