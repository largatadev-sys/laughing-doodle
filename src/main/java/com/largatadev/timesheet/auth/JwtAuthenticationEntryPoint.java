package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.error.ErrorCode;
import com.largatadev.timesheet.error.ErrorEnvelope;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Writes the standard error envelope for requests rejected by the security filter chain
 * itself (before a request ever reaches a controller, so GlobalExceptionHandler never sees it). */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {

		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		ErrorEnvelope body = ErrorEnvelope.of(ErrorCode.UNAUTHENTICATED, "Authentication required");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
