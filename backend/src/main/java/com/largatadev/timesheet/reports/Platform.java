package com.largatadev.timesheet.reports;

/** Where the report came from, so "which build did this happen on?" never needs a follow-up
 * question — reporters are unreachable by design. */
public enum Platform {
	ANDROID,
	IOS,
	WEB
}
