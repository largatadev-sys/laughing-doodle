package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.auth.JwtService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EditEntryEndpointTest {

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

	@Autowired
	TimeEntryRepository timeEntryRepository;

	User member1;
	User member2;
	String tokenForMember1;

	@BeforeEach
	void setUp() {
		timeEntryRepository.deleteAll();
		member1 = userRepository.findByUsername("member1").orElseThrow();
		member2 = userRepository.findByUsername("member2").orElseThrow();
		tokenForMember1 = jwtService.issue(member1.getId(), member1.getRole());
	}

	private TimeEntry seedEntry(Long userId, LocalDate entryDate, int durationMin, String description) {
		OffsetDateTime now = OffsetDateTime.now();
		return timeEntryRepository.save(new TimeEntry(userId, entryDate, durationMin, description, now, now));
	}

	private String updateJson(LocalDate entryDate, Integer durationMin, String description) throws Exception {
		return objectMapper.writeValueAsString(new UpdateEntryRequest(entryDate, durationMin, description));
	}

	@Test
	void authorEditsOwnEntryReturns200WithUpdatedFields() throws Exception {
		TimeEntry seeded = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");
		TimeEntry entry = timeEntryRepository.findById(seeded.getId()).orElseThrow();

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember1)
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 90, "Updated work")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(entry.getId()))
				.andExpect(jsonPath("$.userId").value(member1.getId()))
				.andExpect(jsonPath("$.authorName").value("Member One"))
				.andExpect(jsonPath("$.entryDate").value("2026-07-02"))
				.andExpect(jsonPath("$.durationMin").value(90))
				.andExpect(jsonPath("$.description").value("Updated work"));

		TimeEntry updated = timeEntryRepository.findById(entry.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(updated.getUpdatedAt()).isAfter(entry.getUpdatedAt());
		org.assertj.core.api.Assertions.assertThat(updated.getCreatedAt()).isEqualTo(entry.getCreatedAt());
	}

	@Test
	void differentUserEditingSomeoneElsesEntryReturns403AndLeavesEntryUnchanged() throws Exception {
		TimeEntry seeded = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");
		TimeEntry entry = timeEntryRepository.findById(seeded.getId()).orElseThrow();
		String tokenForMember2 = jwtService.issue(member2.getId(), member2.getRole());

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember2)
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 90, "Hijacked work")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		TimeEntry stillOriginal = timeEntryRepository.findById(entry.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(stillOriginal.getEntryDate()).isEqualTo(entry.getEntryDate());
		org.assertj.core.api.Assertions.assertThat(stillOriginal.getDurationMin()).isEqualTo(entry.getDurationMin());
		org.assertj.core.api.Assertions.assertThat(stillOriginal.getDescription()).isEqualTo(entry.getDescription());
		org.assertj.core.api.Assertions.assertThat(stillOriginal.getUpdatedAt()).isEqualTo(entry.getUpdatedAt());
	}

	@Test
	void unknownIdReturns404() throws Exception {
		mockMvc.perform(put("/api/entries/999999")
						.header("Authorization", "Bearer " + tokenForMember1)
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 90, "Doesn't matter")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void nonPositiveDurationReturns400ValidationFailed() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember1)
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 0, "Updated work")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.durationMin").exists());
	}

	@Test
	void missingEntryDateReturns400ValidationFailed() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");
		String body = objectMapper.writeValueAsString(java.util.Map.of(
				"durationMin", 30,
				"description", "Updated work"));

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember1)
						.contentType("application/json")
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.entryDate").exists());
	}

	@Test
	void blankDescriptionReturns400ValidationFailed() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember1)
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 30, "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.description").exists());
	}

	@Test
	void noTokenReturns401() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");

		mockMvc.perform(put("/api/entries/" + entry.getId())
						.contentType("application/json")
						.content(updateJson(LocalDate.of(2026, 7, 2), 30, "Updated work")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}
}
