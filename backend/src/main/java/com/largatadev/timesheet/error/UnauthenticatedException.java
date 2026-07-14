package com.largatadev.timesheet.error;

public class UnauthenticatedException extends AppException {

	public UnauthenticatedException(String message) {
		super(ErrorCode.UNAUTHENTICATED, message);
	}
}
