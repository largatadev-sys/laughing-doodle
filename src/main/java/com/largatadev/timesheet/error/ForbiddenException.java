package com.largatadev.timesheet.error;

public class ForbiddenException extends AppException {

	public ForbiddenException(String message) {
		super(ErrorCode.FORBIDDEN, message);
	}
}
