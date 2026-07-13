package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.auth.JwtService;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import tools.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreateEntryEndpointTest {

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

	@Test
	void authenticatedCreateReturns201AndIgnoresBodySuppliedUserId() throws Exception {
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();
		String token = jwtService.issue(member1.getId(), member1.getRole());

		String body = objectMapper.writeValueAsString(Map.of(
				"userId", member2.getId(),
				"entryDate", "2026-07-10",
				"durationMin", 90,
				"description", "Wrote the Story 3 plan"));

		mockMvc.perform(post("/api/entries")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(member1.getId()))
				.andExpect(jsonPath("$.authorName").value("Member One"))
				.andExpect(jsonPath("$.entryDate").value("2026-07-10"))
				.andExpect(jsonPath("$.durationMin").value(90))
				.andExpect(jsonPath("$.description").value("Wrote the Story 3 plan"))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void nonPositiveDurationReturns400ValidationFailed() throws Exception {
		String token = tokenFor("member1");

		mockMvc.perform(post("/api/entries")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(entryJson(LocalDate.of(2026, 7, 10), 0, "Some work")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.durationMin").exists());
	}

	@Test
	void missingEntryDateReturns400ValidationFailed() throws Exception {
		String token = tokenFor("member1");
		String body = objectMapper.writeValueAsString(Map.of(
				"durationMin", 30,
				"description", "Some work"));

		mockMvc.perform(post("/api/entries")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.entryDate").exists());
	}

	@Test
	void blankDescriptionReturns400ValidationFailed() throws Exception {
		String token = tokenFor("member1");

		mockMvc.perform(post("/api/entries")
						.header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(entryJson(LocalDate.of(2026, 7, 10), 30, "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.description").exists());
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/entries")
						.contentType("application/json")
						.content(entryJson(LocalDate.of(2026, 7, 10), 30, "Some work")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private String tokenFor(String username) {
		User user = userRepository.findByUsername(username).orElseThrow();
		return jwtService.issue(user.getId(), user.getRole());
	}

	private String entryJson(LocalDate entryDate, Integer durationMin, String description) throws Exception {
		return objectMapper.writeValueAsString(new CreateEntryRequest(entryDate, durationMin, description));
	}
}
