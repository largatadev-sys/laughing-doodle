package com.largatadev.timesheet.support;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only: exists solely so the JWT filter chain has a protected route to exercise
 * (no real protected endpoint exists yet — Story 3+). Never shipped (src/test only). */
@RestController
@RequestMapping("/api/_test/protected")
public class TestProtectedController {

	@GetMapping
	public Map<String, Object> ping() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
		return Map.of("userId", principal.userId(), "role", principal.role().name());
	}
}
