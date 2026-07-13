package com.largatadev.timesheet.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ErrorEnvelope> handleAppException(AppException ex) {
		if (ex.code() == ErrorCode.INTERNAL) {
			log.error("Unexpected error: {}", ex.getMessage(), ex);
		}
		return ResponseEntity.status(ex.code().status())
				.body(ErrorEnvelope.of(ex.code(), ex.getMessage(), ex.details()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(ErrorCode.INTERNAL.status())
				.body(ErrorEnvelope.of(ErrorCode.INTERNAL, "An unexpected error occurred."));
	}
}
