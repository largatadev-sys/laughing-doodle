package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		return authService.login(request.username(), request.password());
	}

	// Authenticated (falls under /api/** in SecurityConfig): the token's own user changes
	// their password. Identity comes from the token, never the body.
	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestBody ChangePasswordRequest request) {
		authService.changePassword(
				authenticatedUser.userId(), request.currentPassword(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	// Authenticated: the token's own user renames their display name. Returns the updated
	// profile so the client can refresh its session/header.
	@PutMapping("/name")
	public UserSummary updateName(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestBody UpdateNameRequest request) {
		return authService.updateName(authenticatedUser.userId(), request.name());
	}
}
