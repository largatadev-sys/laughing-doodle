package com.largatadev.timesheet.error;

import java.util.Map;

public record ErrorBody(String code, String message, Map<String, Object> details) {
}
