package com.largatadev.timesheet.dashboard.controller;

import com.largatadev.timesheet.dashboard.dto.ActiveSeries;
import com.largatadev.timesheet.dashboard.dto.DashboardSeries;
import com.largatadev.timesheet.dashboard.dto.DashboardSummary;
import com.largatadev.timesheet.dashboard.service.DashboardQueryService;
import com.largatadev.timesheet.dashboard.domain.SeriesBucket;

import com.largatadev.timesheet.error.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * The team-facing Dashboard. Standard bearer-JWT surface: every Member reads the same numbers,
 * and Events have no owner, so there is no ownership check to make. Reads only — this surface
 * has no write, and the intake secret cannot open it (it lives in the other filter chain).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	/** The span a period read may ask about: every possible Event, with a wide margin. */
	static final LocalDate EARLIEST_QUERY_DATE = LocalDate.of(1970, 1, 1);
	static final LocalDate LATEST_QUERY_DATE = LocalDate.of(9999, 12, 31);

	private final DashboardQueryService queryService;

	public DashboardController(DashboardQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/summary")
	public ResponseEntity<DashboardSummary> summary(@RequestParam(required = false) String zone) {
		return ResponseEntity.ok(queryService.summary(ViewerZone.parse(zone)));
	}

	/** Counts per day / month / year for one counter. {@code from} and {@code to} are calendar
	 *  days in the viewer's zone, both inclusive; each defaults per bucket. */
	@GetMapping("/series")
	public ResponseEntity<DashboardSeries> series(
			@RequestParam(required = false) String counter,
			@RequestParam(required = false) String bucket,
			@RequestParam(required = false) String zone,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {

		if (counter == null || counter.isBlank()) {
			throw new ValidationException("Invalid query parameter", Map.of("counter", "is required"));
		}
		return ResponseEntity.ok(queryService.series(
				counter, SeriesBucket.parse(bucket), ViewerZone.parse(zone), day("from", from), day("to", to)));
	}

	/** Travelers active per day / month / year — the same rule as "active today". */
	@GetMapping("/active")
	public ResponseEntity<ActiveSeries> active(
			@RequestParam(required = false) String bucket,
			@RequestParam(required = false) String zone,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {

		return ResponseEntity.ok(queryService.active(
				SeriesBucket.parse(bucket), ViewerZone.parse(zone), day("from", from), day("to", to)));
	}

	private static LocalDate day(String name, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		LocalDate date;
		try {
			date = LocalDate.parse(raw);
		} catch (DateTimeParseException notADate) {
			throw new ValidationException("Invalid query parameter", Map.of(name, "must be a date, YYYY-MM-DD"));
		}
		// ISO dates allow years like +999999999, which overflow the bucket arithmetic and
		// Postgres's timestamp range: bound them here, at the edge, so they are a 400, not a 500.
		if (date.isBefore(EARLIEST_QUERY_DATE) || date.isAfter(LATEST_QUERY_DATE)) {
			throw new ValidationException("Invalid query parameter",
					Map.of(name, "must be between " + EARLIEST_QUERY_DATE + " and " + LATEST_QUERY_DATE));
		}
		return date;
	}
}
