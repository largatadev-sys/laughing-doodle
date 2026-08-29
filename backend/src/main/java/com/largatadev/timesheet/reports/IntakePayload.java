package com.largatadev.timesheet.reports;

/**
 * The `report` JSON part of the intake multipart request — the cross-repo wire contract
 * (spec: docs/tickets/reports-inbox/spec.md). Every field is a raw String even where a
 * typed value is expected: Largata is a separate codebase and a bad value must come back as
 * a `400` naming the field, not as a Jackson deserialization failure. Parsing and validation
 * happen in {@link ReportService}.
 */
public record IntakePayload(
		String reportId,
		String type,
		String description,
		Reporter reporter,
		Context context,
		String submittedAt) {

	public record Reporter(String name, String uid) {
	}

	public record Context(String platform, String appVersion, String screen,
			String os, String browser, String deviceModel) {
	}
}
