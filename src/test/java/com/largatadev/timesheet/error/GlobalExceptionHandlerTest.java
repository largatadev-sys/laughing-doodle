package com.largatadev.timesheet.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void validationExceptionYields400WithEnvelope() {
		ResponseEntity<ErrorEnvelope> response =
				handler.handleAppException(new ValidationException("bad input"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().error().message()).isEqualTo("bad input");
	}

	@Test
	void unauthenticatedExceptionYields401WithEnvelope() {
		ResponseEntity<ErrorEnvelope> response =
				handler.handleAppException(new UnauthenticatedException("not authenticated"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().error().code()).isEqualTo("UNAUTHENTICATED");
		assertThat(response.getBody().error().message()).isEqualTo("not authenticated");
	}

	@Test
	void forbiddenExceptionYields403WithEnvelope() {
		ResponseEntity<ErrorEnvelope> response =
				handler.handleAppException(new ForbiddenException("not allowed"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody().error().code()).isEqualTo("FORBIDDEN");
		assertThat(response.getBody().error().message()).isEqualTo("not allowed");
	}

	@Test
	void notFoundExceptionYields404WithEnvelope() {
		ResponseEntity<ErrorEnvelope> response =
				handler.handleAppException(new NotFoundException("no such thing"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
		assertThat(response.getBody().error().message()).isEqualTo("no such thing");
	}

	@Test
	void conflictExceptionYields409WithEnvelope() {
		ResponseEntity<ErrorEnvelope> response =
				handler.handleAppException(new ConflictException("already exists"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
		assertThat(response.getBody().error().message()).isEqualTo("already exists");
	}

	@Test
	void uncaughtExceptionYields500WithGenericMessageAndNoLeakedDetail() {
		Exception raw = new RuntimeException("boom, with a raw stack trace and SQL: SELECT * FROM users");

		ResponseEntity<ErrorEnvelope> response = handler.handleUnexpected(raw);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().error().code()).isEqualTo("INTERNAL");
		assertThat(response.getBody().error().message())
				.isEqualTo("An unexpected error occurred.")
				.doesNotContain("SELECT")
				.doesNotContain("RuntimeException");
	}
}
