package com.largatadev.timesheet.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		extractBearerToken(request)
				.flatMap(jwtService::verify)
				.ifPresent(this::populateSecurityContext);

		filterChain.doFilter(request, response);
	}

	private Optional<String> extractBearerToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			return Optional.empty();
		}
		return Optional.of(header.substring("Bearer ".length()));
	}

	private void populateSecurityContext(AuthenticatedUser authenticatedUser) {
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role().name()));
		var authentication = new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
