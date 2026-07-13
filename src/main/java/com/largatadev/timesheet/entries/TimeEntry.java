package com.largatadev.timesheet.entries;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "time_entries")
public class TimeEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	@Column(name = "duration_min", nullable = false)
	private Integer durationMin;

	@Column(nullable = false)
	private String description;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected TimeEntry() {
	}

	public TimeEntry(Long userId, LocalDate entryDate, Integer durationMin, String description,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		this.userId = userId;
		this.entryDate = entryDate;
		this.durationMin = durationMin;
		this.description = description;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDate getEntryDate() {
		return entryDate;
	}

	public Integer getDurationMin() {
		return durationMin;
	}

	public String getDescription() {
		return description;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
