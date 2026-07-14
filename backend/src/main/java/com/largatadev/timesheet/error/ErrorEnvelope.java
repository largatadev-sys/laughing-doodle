package com.largatadev.timesheet.error;

import java.util.Map;

public record ErrorEnvelope(ErrorBody error) {

	public static ErrorEnvelope of(ErrorCode code, String message) {
		return new ErrorEnvelope(new ErrorBody(code.name(), message, Map.of()));
	}

	public static ErrorEnvelope of(ErrorCode code, String message, Map<String, Object> details) {
		return new ErrorEnvelope(new ErrorBody(code.name(), message, details));
	}
}
