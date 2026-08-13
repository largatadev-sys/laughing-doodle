package com.largatadev.timesheet.reports;

/** Triage state. Any Member may move a Report between any two of these — there are no
 * enforced transitions and no ownership; only NEW is special, as the thing the tab badge counts. */
public enum ReportStatus {
	NEW,
	DISCUSS,
	IN_PROGRESS,
	DONE,
	DISMISSED
}
