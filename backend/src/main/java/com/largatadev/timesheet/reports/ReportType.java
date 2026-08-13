package com.largatadev.timesheet.reports;

/** What a reporter is telling us. Deliberately only two: reporters must never be asked to
 * classify their feedback into categories they don't understand (spec, user story 3). */
public enum ReportType {
	PROBLEM,
	IDEA
}
