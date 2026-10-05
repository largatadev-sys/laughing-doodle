package com.largatadev.timesheet.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * The Dashboard's headline numbers (spec, "Team-facing API"). {@code silentSince} is set only
 * when {@code silent} and a Snapshot has ever arrived; with no Snapshot at all it is null.
 */
public record DashboardSummary(
		Instant asOf,
		String zone,
		Instant lastEventAt,
		Instant lastSnapshotAt,
		boolean silent,
		Instant silentSince,
		long activeToday,
		List<CounterTotal> totals) {

	/**
	 * One running total. {@code baseline} is the occurredAt of the Snapshot the value is
	 * re-based on, or {@code "none"} when no Snapshot has ever carried this counter — the
	 * value is then created minus deleted since Events began.
	 */
	public record CounterTotal(String counter, long value, String baseline) {
	}
}
