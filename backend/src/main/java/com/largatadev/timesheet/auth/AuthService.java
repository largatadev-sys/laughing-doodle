package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.error.UnauthenticatedException;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import com.largatadev.timesheet.users.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

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
}
