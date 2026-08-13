package com.largatadev.timesheet.reports;

/**
 * The only write the team-facing inbox has. Deliberately carries the status and nothing else —
 * who made the change is taken from the JWT, never from here, and a Report's content is never
 * editable by worklog (it is the reporter's words).
 */
public record UpdateStatusRequest(String status) {
}
