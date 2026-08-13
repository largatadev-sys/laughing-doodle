package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates the Largata relay on the intake route by a shared secret (ADR-010) — the only
 * non-JWT authentication in the API besides login. It authenticates a <em>machine</em>, not a
 * user: no worklog identity is established, so nothing downstream can mistake the relay for a
 * Member and INV-2's surface is untouched.
 *
 * <p>Runs only in the intake filter chain, so the JWT chain (and therefore every team route)
 * is unaffected — the two schemes cannot bleed into each other. It is deliberately NOT a
 * {@code @Component}: Boot would then also register it as a plain servlet filter across every
 * request, and it would 401 the whole API. It is constructed by {@code SecurityConfig}, which
 * installs it in the intake chain and nowhere else.
 */
public class IntakeSecretFilter extends OncePerRequestFilter {

	static final String SECRET_HEADER = "X-Intake-Secret";

	private final byte[] expectedSecret;
	private final ErrorResponseWriter errorResponseWriter;

	public IntakeSecretFilter(String intakeSecret, ErrorResponseWriter errorResponseWriter) {
		this.expectedSecret = intakeSecret.getBytes(StandardCharsets.UTF_8);
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		if (!secretMatches(request.getHeader(SECRET_HEADER))) {
			// Deliberately says nothing about which part was wrong, and never logs the header —
			// the secret must not reach a log file or an error body. Same envelope the JWT
			// chain returns, from the same writer, so the two 401s cannot drift apart.
			errorResponseWriter.writeUnauthenticated(response);
			return;
		}

		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"intake-relay",
				"intake-relay",
				List.of(new SimpleGrantedAuthority("ROLE_INTAKE"))));

		filterChain.doFilter(request, response);
	}

	private boolean secretMatches(String presented) {
		if (presented == null) {
			return false;
		}
		// Constant-time: a length-independent, byte-by-byte comparison so response timing
		// cannot be used to guess the secret one character at a time.
		return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedSecret);
	}

}
