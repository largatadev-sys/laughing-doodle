package com.largatadev.timesheet.entries;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EntryResponse(
		Long id,
		Long userId,
		String authorName,
		LocalDate entryDate,
		Integer durationMin,
		String description,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	public static EntryResponse of(TimeEntry entry, String authorName) {
		return new EntryResponse(
				entry.getId(),
				entry.getUserId(),
				authorName,
				entry.getEntryDate(),
				entry.getDurationMin(),
				entry.getDescription(),
				entry.getCreatedAt(),
				entry.getUpdatedAt());
	}
}
