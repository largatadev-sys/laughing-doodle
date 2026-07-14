package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UpdateNameEndpointTest {

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

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	User member3;
	String token;

	// member3's name is reset before each test so the class is order-independent.
	@BeforeEach
	void setUp() {
		member3 = userRepository.findByUsername("member3").orElseThrow();
		member3.rename("Member Three");
		userRepository.save(member3);
		token = jwtService.issue(member3.getId(), member3.getRole());
	}

	private String nameJson(String name) throws Exception {
		return objectMapper.writeValueAsString(new UpdateNameRequest(name));
	}

	@Test
	void validRenameReturns200WithUpdatedProfileAndPersists() throws Exception {
		mockMvc.perform(put("/api/auth/name")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(nameJson("  Jordan Rivera  ")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Jordan Rivera")) // trimmed
				.andExpect(jsonPath("$.username").value("member3"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User reloaded = userRepository.findByUsername("member3").orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getName()).isEqualTo("Jordan Rivera");
	}

	@Test
	void blankNameReturns400() throws Exception {
		mockMvc.perform(put("/api/auth/name")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(nameJson("   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.name").isNotEmpty());
	}

	@Test
	void tooLongNameReturns400() throws Exception {
		String tooLong = "x".repeat(101);
		mockMvc.perform(put("/api/auth/name")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(nameJson(tooLong)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.name").isNotEmpty());
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(put("/api/auth/name")
						.contentType("application/json")
						.content(nameJson("Whoever")))
				.andExpect(status().isUnauthorized());
	}
}
