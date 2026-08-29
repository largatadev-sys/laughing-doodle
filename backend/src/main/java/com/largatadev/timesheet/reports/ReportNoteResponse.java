package com.largatadev.timesheet.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Note as the team API returns it. Names are resolved server-side, the same way a Report
 * carries {@code statusChangedByName} — the client holds no user directory.
 *
 * <p>{@code editedBy}/{@code editedByName}/{@code editedAt} are null until someone edits, which
 * is exactly how the client decides whether to render the "Edited" stamp.
 */
public record ReportNoteResponse(
		UUID id,
		String body,
		Long authorId,
		String authorName,
		OffsetDateTime createdAt,
		Long editedBy,
		String editedByName,
		OffsetDateTime editedAt) {

	public static ReportNoteResponse of(ReportNote note, String authorName, String editedByName) {
		return new ReportNoteResponse(
				note.getId(),
				note.getBody(),
				note.getAuthorId(),
				authorName,
				note.getCreatedAt(),
				note.getEditedBy(),
				note.getEditedBy() == null ? null : editedByName,
				note.getEditedAt());
	}
}
