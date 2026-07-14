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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListEntriesEndpointTest {

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

		seedEntry(member1.getId(), LocalDate.of(2026, 7, 1), 60, "Member1 work on the 1st");
		seedEntry(member2.getId(), LocalDate.of(2026, 7, 5), 90, "Member2 work on the 5th");
		seedEntry(member1.getId(), LocalDate.of(2026, 7, 10), 30, "Member1 work on the 10th");
	}

	private void seedEntry(Long userId, LocalDate entryDate, int durationMin, String description) {
		OffsetDateTime now = OffsetDateTime.now();
		timeEntryRepository.save(new TimeEntry(userId, entryDate, durationMin, description, now, now));
	}

	@Test
	void noFiltersReturnsEveryUsersEntries() throws Exception {
		mockMvc.perform(get("/api/entries").header("Authorization", "Bearer " + tokenForMember1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].authorName").value("Member One"))
				.andExpect(jsonPath("$[0].entryDate").value("2026-07-01"))
				.andExpect(jsonPath("$[1].authorName").value("Member Two"))
				.andExpect(jsonPath("$[1].entryDate").value("2026-07-05"))
				.andExpect(jsonPath("$[2].authorName").value("Member One"))
				.andExpect(jsonPath("$[2].entryDate").value("2026-07-10"));
	}

	@Test
	void dateRangeFiltersByEntryDate() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("from", "2026-07-02")
						.param("to", "2026-07-08"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].entryDate").value("2026-07-05"));
	}

	@Test
	void onlyFromFilterLeavesUpperBoundOpen() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("from", "2026-07-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void userIdFilterReturnsOnlyThatAuthorsEntries() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("userId", member2.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].authorName").value("Member Two"));
	}

	@Test
	void dateRangeAndUserIdFiltersComposeTogether() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("from", "2026-07-01")
						.param("to", "2026-07-05")
						.param("userId", member1.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].entryDate").value("2026-07-01"))
				.andExpect(jsonPath("$[0].authorName").value("Member One"));
	}

	@Test
	void invertedDateRangeReturnsEmptyArrayNot400() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("from", "2026-07-10")
						.param("to", "2026-07-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void nonExistentUserIdReturnsEmptyArrayNotAnError() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("userId", "999999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void malformedFromReturns400ValidationFailed() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("from", "not-a-date"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void malformedToReturns400ValidationFailed() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("to", "not-a-date"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void malformedUserIdReturns400ValidationFailed() throws Exception {
		mockMvc.perform(get("/api/entries")
						.header("Authorization", "Bearer " + tokenForMember1)
						.param("userId", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/entries"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}
}
