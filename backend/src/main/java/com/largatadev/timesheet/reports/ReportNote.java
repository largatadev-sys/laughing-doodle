package com.largatadev.timesheet.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One team-authored note on a Report — the record of why a decision was made, kept where the
 * decision was made (ADR-012). "Status says where, a Note says why."
 *
 * <p>Append-only as a <em>log</em>: nothing in this class or its repository can remove a note,
 * and no route exists to. The body is mutable, but only by the note's own author (ADR-012 as
 * revised) — a Note is signed testimony, the same ownership shape INV-2 gives a time entry —
 * and every edit stamps who and when. A reporter never authors, reads, or sees these.
 */
@Entity
@Table(name = "report_notes")
public class ReportNote {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "report_id", nullable = false)
	private UUID reportId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "edited_by")
	private Long editedBy;

	@Column(name = "edited_at")
	private OffsetDateTime editedAt;

	protected ReportNote() {
	}

	public ReportNote(UUID reportId, Long authorId, String body, OffsetDateTime createdAt) {
		this.reportId = reportId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getReportId() {
		return reportId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public Long getEditedBy() {
		return editedBy;
	}

	public OffsetDateTime getEditedAt() {
		return editedAt;
	}

	/**
	 * Replace the text and stamp the editor. Author and createdAt are deliberately untouched:
	 * the log's shape never changes, only what a given entry says. The stamp is replaced rather
	 * than accumulated — a note carries its last editor, not a revision history.
	 */
	public void edit(String body, Long editorUserId, OffsetDateTime editedAt) {
		this.body = body;
		this.editedBy = editorUserId;
		this.editedAt = editedAt;
	}
}
