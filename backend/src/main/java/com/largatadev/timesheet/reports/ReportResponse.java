package com.largatadev.timesheet.reports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A Report as the API returns it — to the relay on intake, and to Members on the team routes.
 * Never carries screenshot bytes: only the ordinals, which the client fetches individually
 * from the screenshot route.
 *
 * <p>{@code notes} is the team's own writing and is populated on the team routes only. The
 * intake path passes an empty list on every response, replay included: Largata relays reports
 * in, it never reads what the team said about them.
 */
public record ReportResponse(
		UUID id,
		String type,
		String description,
		String reporterName,
		String reporterUid,
		String platform,
		String appVersion,
		String screen,
		String os,
		String browser,
		String deviceModel,
		OffsetDateTime submittedAt,
		OffsetDateTime receivedAt,
		String status,
		Long statusChangedBy,
		String statusChangedByName,
		OffsetDateTime statusChangedAt,
		List<Integer> screenshotOrdinals,
		List<ReportNoteResponse> notes) {

	public static ReportResponse of(Report report, String statusChangedByName,
			List<Integer> screenshotOrdinals, List<ReportNoteResponse> notes) {
		return new ReportResponse(
				report.getId(),
				wire(report.getType()),
				report.getDescription(),
				report.getReporterName(),
				report.getReporterUid(),
				wire(report.getPlatform()),
				report.getAppVersion(),
				report.getScreen(),
				report.getOs(),
				report.getBrowser(),
				report.getDeviceModel(),
				report.getSubmittedAt(),
				report.getReceivedAt(),
				wire(report.getStatus()),
				report.getStatusChangedBy(),
				statusChangedByName,
				report.getStatusChangedAt(),
				screenshotOrdinals,
				notes);
	}

	/** Enums travel over the wire in the same lowercase form they take in the database
	 * (`problem`, `in_progress`, `ios`) — one spelling across the whole system. */
	private static String wire(Enum<?> value) {
		return value == null ? null : value.name().toLowerCase();
	}
}
