package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.error.UnauthenticatedException;
import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import com.largatadev.timesheet.users.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MAX_NAME_LENGTH = 100; // matches users.name VARCHAR(100)

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public LoginResponse login(String username, String password) {
		User user = userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new UnauthenticatedException(INVALID_CREDENTIALS_MESSAGE));

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new UnauthenticatedException(INVALID_CREDENTIALS_MESSAGE);
		}

		String token = jwtService.issue(user.getId(), user.getRole());
		return new LoginResponse(token, UserSummary.of(user));
	}

	// Self-service password change for the authenticated user. A wrong current password is a
	// field validation error (400), NOT a 401 — a 401 would trip the client into logging the
	// user out mid-change. The current-password check re-verifies identity before the write.
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UnauthenticatedException(INVALID_CREDENTIALS_MESSAGE));

		Map<String, Object> details = new HashMap<>();
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			details.put("currentPassword", "is incorrect");
		}
		if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
			details.put("newPassword", "must be at least " + MIN_PASSWORD_LENGTH + " characters");
		} else if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			details.put("newPassword", "must be different from your current password");
		}

		if (!details.isEmpty()) {
			throw new ValidationException("Could not change your password", details);
		}

		user.changePassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}

	// Self-service display-name change for the authenticated user. Returns the updated profile
	// so the client can refresh its stored session (the header shows this name).
	public UserSummary updateName(Long userId, String name) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UnauthenticatedException(INVALID_CREDENTIALS_MESSAGE));

		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw new ValidationException("Your name is required", Map.of("name", "is required"));
		}
		if (trimmed.length() > MAX_NAME_LENGTH) {
			throw new ValidationException(
					"Your name is too long", Map.of("name", "must be at most " + MAX_NAME_LENGTH + " characters"));
		}

		user.rename(trimmed);
		userRepository.save(user);
		return UserSummary.of(user);
	}
}
