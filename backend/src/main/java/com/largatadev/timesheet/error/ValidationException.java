package com.largatadev.timesheet.error;

import java.util.Map;

public class ValidationException extends AppException {

	public ValidationException(String message) {
		super(ErrorCode.VALIDATION_FAILED, message);
	}

	public ValidationException(String message, Map<String, Object> details) {
		super(ErrorCode.VALIDATION_FAILED, message, details);
	}
}
