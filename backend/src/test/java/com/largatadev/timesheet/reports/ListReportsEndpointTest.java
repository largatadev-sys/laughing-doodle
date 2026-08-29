package com.largatadev.timesheet.reports;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListReportsEndpointTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ReportRepository reportRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		reportRepository.deleteAll();
	}

	@Test
	void emptyInboxReturns200AndEmptyArray() throws Exception {
		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void reportsComeBackNewestFirstBySubmittedAtRegardlessOfArrivalOrder() throws Exception {
		// Out-of-order arrival: the oldest submission is received last (a retried delivery).
		save("Newest submission", OffsetDateTime.of(2026, 8, 12, 10, 0, 0, 0, ZoneOffset.UTC));
		save("Middle submission", OffsetDateTime.of(2026, 8, 11, 10, 0, 0, 0, ZoneOffset.UTC));
		save("Oldest submission", OffsetDateTime.of(2026, 8, 10, 10, 0, 0, 0, ZoneOffset.UTC));

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].description").value("Newest submission"))
				.andExpect(jsonPath("$[1].description").value("Middle submission"))
				.andExpect(jsonPath("$[2].description").value("Oldest submission"));
	}

	@Test
	void statusFilterNarrowsTheList() throws Exception {
		Report untouched = save("Still new", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
		Report parked = save("Parked for the founders", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
		parked.changeStatus(ReportStatus.DISCUSS, 1L, OffsetDateTime.now(ZoneOffset.UTC));
		reportRepository.save(parked);

		mockMvc.perform(get("/api/reports?status=discuss").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(parked.getId().toString()));

		mockMvc.perform(get("/api/reports?status=new").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(untouched.getId().toString()));
	}

	@Test
	void deviceContextTravelsOnTheTeamList() throws Exception {
		// Contract v1.2: the team list carries what the reporter was running, nulls included
		// for pre-v1.2 rows — the client's Device row is built from these three verbatim.
		reportRepository.save(new Report(
				UUID.randomUUID(),
				ReportType.PROBLEM,
				"Blank map on web.",
				"Ada Traveler",
				"largata-uid-1",
				Platform.WEB,
				"1.4.2",
				"(tabs)/(trips)/map",
				"Windows 11",
				"Chrome 128",
				"Pixel 6",
				OffsetDateTime.now(ZoneOffset.UTC),
				OffsetDateTime.now(ZoneOffset.UTC)));

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].os").value("Windows 11"))
				.andExpect(jsonPath("$[0].browser").value("Chrome 128"))
				.andExpect(jsonPath("$[0].deviceModel").value("Pixel 6"));
	}

	@Test
	void unknownStatusFilterReturns400() throws Exception {
		mockMvc.perform(get("/api/reports?status=archived").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.status").exists());
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/reports"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void theIntakeSecretDoesNotOpenTheTeamList() throws Exception {
		// Mirror of the intake test's "a JWT doesn't open intake" — neither scheme crosses over.
		mockMvc.perform(get("/api/reports").header("X-Intake-Secret", "any-value-at-all"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private Report save(String description, OffsetDateTime submittedAt) {
		Report report = new Report(
				UUID.randomUUID(),
				ReportType.PROBLEM,
				description,
				"Ada Traveler",
				"largata-uid-1",
				Platform.ANDROID,
				"1.4.2",
				"(tabs)/(home)",
				null, null, null,
				submittedAt,
				OffsetDateTime.now(ZoneOffset.UTC));
		return reportRepository.save(report);
	}

	private String memberToken() {
		User member = userRepository.findByUsername("member1").orElseThrow();
		return jwtService.issue(member.getId(), member.getRole());
	}
}
