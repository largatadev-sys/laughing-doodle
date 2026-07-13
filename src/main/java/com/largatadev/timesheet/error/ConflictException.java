package com.largatadev.timesheet.error;

public class ConflictException extends AppException {

	public ConflictException(String message) {
		super(ErrorCode.CONFLICT, message);
	}
}
