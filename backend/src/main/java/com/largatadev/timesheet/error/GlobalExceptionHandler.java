package com.largatadev.timesheet.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorEnvelope> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		// The servlet container refuses an oversized part before any controller runs. Without
		// this handler it would fall to the catch-all below and return 500 — and the Largata
		// relay's store-and-forward loop retries until it sees a 2xx, so a permanently-oversized
		// payload would be retried forever. A 400 tells it the report is bad, not the server.
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
				.body(ErrorEnvelope.of(ErrorCode.VALIDATION_FAILED, "Invalid report",
						Map.of("screenshot", "must be at most 5 MB")));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorEnvelope> handleNoResource(NoResourceFoundException ex) {
		// A missing static file (e.g. an unknown asset under the SPA's static/ root). Routine —
		// don't log as an error; return a clean 404 instead of the catch-all's 500. See ADR-008.
		return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
				.body(ErrorEnvelope.of(ErrorCode.NOT_FOUND, "Resource not found."));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		// A method nobody mapped — e.g. DELETE on a report note, which deliberately has no route
		// (ADR-012). Without this it fell to the catch-all: a 500 and an ERROR log line for what
		// is really "there is no such thing here". Reported as 404 rather than 405 so the error
		// vocabulary stays the five documented codes (05-api-conventions); for this API an
		// unmapped method IS an absent resource.
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
