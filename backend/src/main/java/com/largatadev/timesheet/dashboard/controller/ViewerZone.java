package com.largatadev.timesheet.dashboard.controller;

import com.largatadev.timesheet.error.ValidationException;

import java.time.ZoneId;
import java.util.Map;

/**
 * The viewer's time zone, as the client sends it. "Today" and every bucket are the viewer's
 * calendar, so the server never guesses: absent means UTC, and anything that is not an IANA
 * zone name is a {@code 400} with key {@code zone} rather than a silent fallback.
 */
final class ViewerZone {

	static final ZoneId DEFAULT = ZoneId.of("UTC");

	private ViewerZone() {
	}

	static ZoneId parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return DEFAULT;
		}
		// The tz database's names only: a bare offset ("+08:00") or an abbreviation ("EST")
		// has no daylight-saving rules, so it would put an Event on the wrong day for half the
		// year without anyone noticing.
		if (!ZoneId.getAvailableZoneIds().contains(raw)) {
			throw new ValidationException("Invalid query parameter",
					Map.of("zone", "must be an IANA time zone name, e.g. Asia/Manila"));
		}
		return ZoneId.of(raw);
	}
}
