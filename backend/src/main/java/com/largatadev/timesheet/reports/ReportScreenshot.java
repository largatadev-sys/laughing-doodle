package com.largatadev.timesheet.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * One image attached to a Report, bytes and all. Keyed by (report, ordinal) rather than a
 * surrogate id so a redelivery of the same screenshot slot collides at the database instead of
 * silently writing a second copy — the same idempotency trick the Report's own key uses.
 */
@Entity
@Table(name = "report_screenshots")
public class ReportScreenshot {

	@EmbeddedId
	private Key key;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	// Plain byte[] maps to Postgres `bytea`. NOT @Lob: on Postgres that means a large-object
	// OID column (a bigint pointing into pg_largeobject), which does not match this schema and
	// would need explicit LO cleanup on delete.
	@Column(name = "bytes", nullable = false)
	private byte[] bytes;

	protected ReportScreenshot() {
	}

	public ReportScreenshot(UUID reportId, int ordinal, String contentType, byte[] bytes) {
		this.key = new Key(reportId, (short) ordinal);
		this.contentType = contentType;
		this.bytes = bytes;
	}

	public UUID getReportId() {
		return key.getReportId();
	}

	public int getOrdinal() {
		return key.getOrdinal();
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getBytes() {
		return bytes;
	}

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "report_id", nullable = false)
		private UUID reportId;

		@Column(name = "ordinal", nullable = false)
		private Short ordinal;

		protected Key() {
		}

		Key(UUID reportId, Short ordinal) {
			this.reportId = reportId;
			this.ordinal = ordinal;
		}

		UUID getReportId() {
			return reportId;
		}

		int getOrdinal() {
			return ordinal;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			return other instanceof Key key
					&& Objects.equals(reportId, key.reportId)
					&& Objects.equals(ordinal, key.ordinal);
		}

		@Override
		public int hashCode() {
			return Objects.hash(reportId, ordinal);
		}
	}
}
