package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.auth.JwtService;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DeleteEntryEndpointTest {

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

	@Test
	void authorDeletingOwnEntryReturns204AndEntryIsGone() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");

		mockMvc.perform(delete("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember1))
				.andExpect(status().isNoContent());

		assertThat(timeEntryRepository.findById(entry.getId())).isEmpty();
	}

	@Test
	void differentUserDeletingSomeoneElsesEntryReturns403AndLeavesEntryPresent() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");
		String tokenForMember2 = jwtService.issue(member2.getId(), member2.getRole());

		mockMvc.perform(delete("/api/entries/" + entry.getId())
						.header("Authorization", "Bearer " + tokenForMember2))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(timeEntryRepository.findById(entry.getId())).isPresent();
	}

	@Test
	void unknownIdReturns204NoOp() throws Exception {
		mockMvc.perform(delete("/api/entries/999999")
						.header("Authorization", "Bearer " + tokenForMember1))
				.andExpect(status().isNoContent());
	}

	@Test
	void noTokenReturns401() throws Exception {
		TimeEntry entry = seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Original work");

		mockMvc.perform(delete("/api/entries/" + entry.getId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(timeEntryRepository.findById(entry.getId())).isPresent();
	}
}
