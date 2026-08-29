package com.largatadev.timesheet.reports;

/**
 * The whole write body for both note routes. There is no author or editor field by design:
 * identity comes from the JWT, so there is nothing here to spoof.
 */
public record NoteRequest(String body) {
}
