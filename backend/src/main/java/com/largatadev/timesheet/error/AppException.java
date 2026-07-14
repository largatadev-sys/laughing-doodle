package com.largatadev.timesheet.error;

import java.util.Map;

public abstract class AppException extends RuntimeException {

	private final ErrorCode code;
	private final Map<String, Object> details;

	protected AppException(ErrorCode code, String message) {
		this(code, message, Map.of());
	}

	protected AppException(ErrorCode code, String message, Map<String, Object> details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, Object> details() {
		return details;
	}
}
