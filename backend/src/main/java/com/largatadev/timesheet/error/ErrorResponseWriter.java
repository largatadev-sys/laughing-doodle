package com.largatadev.timesheet.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes the standard error envelope straight to the response, for rejections that happen
 * inside the security filter chain — before a request reaches a controller, so
 * {@link GlobalExceptionHandler} never sees them. Both authentication schemes (bearer JWT and
 * the relay's shared secret) go through here, so the 401 body has exactly one definition.
 */
@Component
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public ErrorResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void writeUnauthenticated(HttpServletResponse response) throws IOException {
		write(response, ErrorCode.UNAUTHENTICATED, "Authentication required");
	}

	public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType("application/json");
		response.getWriter().write(objectMapper.writeValueAsString(ErrorEnvelope.of(code, message)));
	}
}
