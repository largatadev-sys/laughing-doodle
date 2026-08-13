package com.largatadev.timesheet.reports;

/** One validated image part from the relay, in the order it arrived (its ordinal). */
public record IncomingScreenshot(String contentType, byte[] bytes) {
}
