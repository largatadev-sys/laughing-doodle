package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.error.UnauthenticatedException;
import com.largatadev.timesheet.users.Role;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

	@Mock
	UserRepository userRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	JwtService jwtService;

	@Mock
	User user;

	AuthService authService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		authService = new AuthService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void correctPasswordDelegatesToPasswordEncoderAndIssuesToken() {
		when(userRepository.findByUsername("member1")).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(1L);
		when(user.getName()).thenReturn("Test User");
		when(user.getUsername()).thenReturn("member1");
		when(user.getRole()).thenReturn(Role.MEMBER);
		when(user.getPasswordHash()).thenReturn("stored-hash");
		when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
		when(jwtService.issue(1L, Role.MEMBER)).thenReturn("a-signed-token");

		LoginResponse response = authService.login("member1", "correct-password");

		assertThat(response.token()).isEqualTo("a-signed-token");
		assertThat(response.user().username()).isEqualTo("member1");
	}

	@Test
	void wrongPasswordCallsPasswordEncoderAndThrowsUnauthenticated() {
		when(userRepository.findByUsername("member1")).thenReturn(Optional.of(user));
		when(user.getPasswordHash()).thenReturn("stored-hash");
		when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.login("member1", "wrong-password"))
				.isInstanceOf(UnauthenticatedException.class)
				.hasMessage("Invalid username or password");
	}

	@Test
	void unknownUsernameNeverCallsPasswordEncoderAndThrowsUnauthenticated() {
		when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("nobody", "irrelevant"))
				.isInstanceOf(UnauthenticatedException.class)
				.hasMessage("Invalid username or password");

		org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
	}

	@Test
	void usernameIsLowercasedBeforeRepositoryLookup() {
		when(userRepository.findByUsername("member1")).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(1L);
		when(user.getName()).thenReturn("Test User");
		when(user.getUsername()).thenReturn("member1");
		when(user.getRole()).thenReturn(Role.MEMBER);
		when(user.getPasswordHash()).thenReturn("stored-hash");
		when(passwordEncoder.matches("pw", "stored-hash")).thenReturn(true);

		authService.login("MEMBER1", "pw");

		verify(userRepository).findByUsername("member1");
	}
}
