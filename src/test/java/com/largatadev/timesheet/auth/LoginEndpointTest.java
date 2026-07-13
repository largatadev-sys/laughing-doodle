package com.largatadev.timesheet.auth;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoginEndpointTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void correctCredentialsReturn200WithTokenAndUserProfile() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("admin", "changeme123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.id").isNumber())
				.andExpect(jsonPath("$.user.username").value("admin"))
				.andExpect(jsonPath("$.user.role").value("ADMIN"))
				.andExpect(jsonPath("$.user.password_hash").doesNotExist())
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
	}

	@Test
	void wrongPasswordReturns401WithGenericMessage() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("admin", "totally-wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.error.message").value("Invalid username or password"));
	}

	@Test
	void unknownUsernameReturns401WithIdenticalGenericMessage() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("no-such-user", "whatever")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.error.message").value("Invalid username or password"));
	}

	@Test
	void loginIsCaseInsensitiveOnUsername() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson("ADMIN", "changeme123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.username").value("admin"));
	}

	private String loginJson(String username, String password) throws Exception {
		return objectMapper.writeValueAsString(new LoginRequest(username, password));
	}
}
