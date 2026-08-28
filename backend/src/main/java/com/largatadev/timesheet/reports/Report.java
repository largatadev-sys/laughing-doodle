package com.largatadev.timesheet.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One piece of feedback from a Largata user. The reporter is <em>foreign identity as data</em> —
 * name and uid are opaque strings from Largata, never a worklog User, so the auth surface and
 * INV-2 are untouched. Reports have no owner: every Member reads and triages any Report.
 */
@Entity
@Table(name = "reports")
public class Report implements Persistable<UUID> {

	/** Minted by Largata and used as the idempotency key: a redelivery collides on this PK. */
	@Id
	private UUID id;

	@Column(nullable = false)
	private ReportType type;

	@Column(nullable = false)
	private String description;

	// Nullable since contract v1.1: a report filed from a signed-out Largata screen carries
	// no identity at all — and those screens are where "I can't get in" bugs live.
	@Column(name = "reporter_name")
	private String reporterName;

	@Column(name = "reporter_uid")
	private String reporterUid;

	@Column(nullable = false)
	private Platform platform;

	@Column(name = "app_version", nullable = false)
	private String appVersion;

	/** Where the reporter was when they opened the report flow — an opaque Largata-minted
	 * string (contract v1.1), never validated against that app's route table. */
	@Column
	private String screen;

	@Column(name = "submitted_at", nullable = false)
	private OffsetDateTime submittedAt;

	@Column(name = "received_at", nullable = false)
	private OffsetDateTime receivedAt;

	@Column(nullable = false)
	private ReportStatus status;

	@Column(name = "status_changed_by")
	private Long statusChangedBy;

	@Column(name = "status_changed_at")
	private OffsetDateTime statusChangedAt;

	// The id is client-assigned, so Spring Data can't tell "new" from "detached" by id alone and
	// would issue a SELECT-then-INSERT. Declaring newness explicitly makes the first save a plain
	// INSERT, which is what lets the primary key — not a prior read — enforce idempotency.
	@Transient
	private boolean isNew;

	protected Report() {
	}

	public Report(UUID id, ReportType type, String description, String reporterName, String reporterUid,
			Platform platform, String appVersion, String screen, OffsetDateTime submittedAt,
			OffsetDateTime receivedAt) {
		this.id = id;
		this.type = type;
		this.description = description;
		this.reporterName = reporterName;
		this.reporterUid = reporterUid;
		this.platform = platform;
		this.appVersion = appVersion;
		this.screen = screen;
		this.submittedAt = submittedAt;
		this.receivedAt = receivedAt;
		this.status = ReportStatus.NEW;
		this.isNew = true;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	// Once the row exists (freshly inserted, or loaded from the database) the entity is no
	// longer new — otherwise a later save() of the same instance would try to INSERT again.
	@PostPersist
	@PostLoad
	void markNotNew() {
		this.isNew = false;
	}

	public ReportType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public String getReporterName() {
		return reporterName;
	}

	public String getReporterUid() {
		return reporterUid;
	}

	public Platform getPlatform() {
		return platform;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public String getScreen() {
		return screen;
	}

	public OffsetDateTime getSubmittedAt() {
		return submittedAt;
	}

	public OffsetDateTime getReceivedAt() {
		return receivedAt;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public Long getStatusChangedBy() {
		return statusChangedBy;
	}

	public OffsetDateTime getStatusChangedAt() {
		return statusChangedAt;
	}

	/** Free movement between any two statuses by any Member; the mover is recorded for
	 * attribution, never taken from a request body. */
	public void changeStatus(ReportStatus status, Long changedByUserId, OffsetDateTime changedAt) {
		this.status = status;
		this.statusChangedBy = changedByUserId;
		this.statusChangedAt = changedAt;
	}
}
