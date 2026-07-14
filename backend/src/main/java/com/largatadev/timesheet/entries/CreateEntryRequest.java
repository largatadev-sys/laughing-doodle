package com.largatadev.timesheet.entries;

import java.time.LocalDate;

public record CreateEntryRequest(LocalDate entryDate, Integer durationMin, String description) {
}
