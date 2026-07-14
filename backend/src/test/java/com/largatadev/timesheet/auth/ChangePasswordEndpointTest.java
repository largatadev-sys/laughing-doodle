package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChangePasswordEndpointTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	private static final String OLD_PASSWORD = "changeme123";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	User member3;
	String token;

	// member3 is not password-authenticated by any other test, so mutating its password here is
	// safe. Reset it before each test for isolation within this class.
	@BeforeEach
	void setUp() {
		member3 = userRepository.findByUsername("member3").orElseThrow();
		member3.changePassword(passwordEncoder.encode(OLD_PASSWORD));
		userRepository.save(member3);
		token = jwtService.issue(member3.getId(), member3.getRole());
	}

	private String changeJson(String current, String next) throws Exception {
		return objectMapper.writeValueAsString(new ChangePasswordRequest(current, next));
	}

	private String loginJson(String username, String password) throws Exception {
		return objectMapper.writeValueAsString(new LoginRequest(username, password));
	}

	@Test
	void validChangeReturns204AndTheNewPasswordWorksWhileTheOldStops() throws Exception {
		mockMvc.perform(put("/api/auth/password")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(changeJson(OLD_PASSWORD, "brand-new-secret")))
				.andExpect(status().isNoContent());

		// New password logs in.
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("member3", "brand-new-secret")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());

		// Old password no longer works.
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("member3", OLD_PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongCurrentPasswordReturns400NotUnauthorized() throws Exception {
		mockMvc.perform(put("/api/auth/password")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(changeJson("not-my-password", "brand-new-secret")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.currentPassword").isNotEmpty());

		// The password was not changed — the original still logs in.
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("member3", OLD_PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void tooShortNewPasswordReturns400() throws Exception {
		mockMvc.perform(put("/api/auth/password")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(changeJson(OLD_PASSWORD, "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.newPassword").isNotEmpty());
	}

	@Test
	void newPasswordSameAsCurrentReturns400() throws Exception {
		mockMvc.perform(put("/api/auth/password")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(changeJson(OLD_PASSWORD, OLD_PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.newPassword").isNotEmpty());
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(put("/api/auth/password")
						.contentType("application/json")
						.content(changeJson(OLD_PASSWORD, "brand-new-secret")))
				.andExpect(status().isUnauthorized());
	}
}
