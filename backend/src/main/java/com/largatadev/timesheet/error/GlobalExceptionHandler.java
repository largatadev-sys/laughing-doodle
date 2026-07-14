package com.largatadev.timesheet.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

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

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		String paramName = ex.getName();
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
				.body(ErrorEnvelope.of(ErrorCode.VALIDATION_FAILED, "Invalid query parameter",
						Map.of(paramName, "could not be parsed")));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorEnvelope> handleNoResource(NoResourceFoundException ex) {
		// A missing static file (e.g. an unknown asset under the SPA's static/ root). Routine —
		// don't log as an error; return a clean 404 instead of the catch-all's 500. See ADR-008.
		return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
				.body(ErrorEnvelope.of(ErrorCode.NOT_FOUND, "Resource not found."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(ErrorCode.INTERNAL.status())
				.body(ErrorEnvelope.of(ErrorCode.INTERNAL, "An unexpected error occurred."));
	}
}
