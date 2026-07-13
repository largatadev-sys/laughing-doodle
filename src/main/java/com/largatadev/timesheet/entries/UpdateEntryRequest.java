package com.largatadev.timesheet.entries;

import java.time.LocalDate;

public record UpdateEntryRequest(LocalDate entryDate, Integer durationMin, String description) {
}
