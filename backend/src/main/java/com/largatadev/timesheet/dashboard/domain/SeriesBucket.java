package com.largatadev.timesheet.dashboard.domain;

import com.largatadev.timesheet.error.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * A series period on the viewer's calendar. Each knows where its bucket starts, where the next
 * one starts, and how the bucket is named on the wire: {@code 2026-09-24}, {@code 2026-09} or
 * {@code 2026}.
 */
public enum SeriesBucket {

	DAY(DateTimeFormatter.ISO_LOCAL_DATE) {
		@Override
		public LocalDate floor(LocalDate date) {
			return date;
		}

		@Override
		public LocalDate next(LocalDate start) {
			return start.plusDays(1);
		}

		@Override
		public LocalDate defaultFrom(LocalDate to) {
			return to.minusDays(29); // the last 30 days, today included
		}
	},

	MONTH(DateTimeFormatter.ofPattern("uuuu-MM")) {
		@Override
		public LocalDate floor(LocalDate date) {
			return date.withDayOfMonth(1);
		}

		@Override
		public LocalDate next(LocalDate start) {
			return start.plusMonths(1);
		}

		@Override
		public LocalDate defaultFrom(LocalDate to) {
			return to.minusMonths(11); // the last 12 months, this one included
		}
	},

	YEAR(DateTimeFormatter.ofPattern("uuuu")) {
		@Override
		public LocalDate floor(LocalDate date) {
			return date.withDayOfYear(1);
		}

		@Override
		public LocalDate next(LocalDate start) {
			return start.plusYears(1);
		}

		@Override
		public LocalDate defaultFrom(LocalDate to) {
			return null; // every year with data: the caller starts at the first Event
		}
	};

	private final DateTimeFormatter label;

	SeriesBucket(DateTimeFormatter label) {
		this.label = label;
	}

	public abstract LocalDate floor(LocalDate date);

	public abstract LocalDate next(LocalDate start);

	/** The default first day for a range ending on {@code to}, or null when it depends on data. */
	public abstract LocalDate defaultFrom(LocalDate to);

	public String label(LocalDate start) {
		return start.format(label);
	}

	public static SeriesBucket parse(String raw) {
		if (raw != null) {
			for (SeriesBucket bucket : values()) {
				if (bucket.name().equalsIgnoreCase(raw)) {
					return bucket;
				}
			}
		}
		throw new ValidationException("Invalid query parameter", Map.of("bucket", "must be day, month or year"));
	}

	public String wireName() {
		return name().toLowerCase();
	}
}
