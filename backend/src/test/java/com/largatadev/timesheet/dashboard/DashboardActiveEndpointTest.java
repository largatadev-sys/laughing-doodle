package com.largatadev.timesheet.dashboard;

import com.largatadev.timesheet.auth.JwtService;
import com.largatadev.timesheet.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.largatadev.timesheet.dashboard.DashboardSummaryEndpointTest.ev;
import static com.largatadev.timesheet.dashboard.DashboardSummaryEndpointTest.snapshot;
import static com.largatadev.timesheet.dashboard.EventIntakeEndpointTest.batch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for Travelers active per period — "active today", one bucket at a time
 * (design v2's hero series). Same rule as the summary: distinct subjects across every kind,
 * in the viewer's calendar. The clock is pinned at 2026-09-24T12:00Z.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardActiveEndpointTest {

	static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
	private static final String TEST_INTAKE_SECRET = "dashboard-active-test-shared-secret";

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("reports.intake-secret", () -> TEST_INTAKE_SECRET);
	}

	@TestBean
	Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM largata_events");
	}

	@Test
	void noTravelerEverNamedAnswersEmptyPoints() throws Exception {
		// A Snapshot names nobody, so it does not start the history.
		ingest(snapshot("2026-09-24T10:00:00Z", "{\"trip\":1}"));

		active("day", "UTC")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bucket").value("day"))
				.andExpect(jsonPath("$.zone").value("UTC"))
				.andExpect(jsonPath("$.points.length()").value(0));
	}

	@Test
	void eachDayCountsDistinctTravelersAcrossEveryKind() throws Exception {
		ingest(
				ev("trip.created", "traveler-1", "2026-09-22T08:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-09-22T09:00:00Z"),
				ev("postcard.created", "traveler-2", "2026-09-22T10:00:00Z"),
				ev("trip.created", null, "2026-09-22T11:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-09-24T09:00:00Z"));

		active("day", "UTC", "2026-09-21", "2026-09-24")
				.andExpect(jsonPath("$.points.length()").value(4))
				.andExpect(jsonPath("$.points[0].start").value("2026-09-21"))
				.andExpect(jsonPath("$.points[0].active").value(0))
				.andExpect(jsonPath("$.points[1].start").value("2026-09-22"))
				.andExpect(jsonPath("$.points[1].active").value(2))
				.andExpect(jsonPath("$.points[2].active").value(0))
				.andExpect(jsonPath("$.points[3].start").value("2026-09-24"))
				.andExpect(jsonPath("$.points[3].active").value(1));
	}

	@Test
	void theDefaultDayRangeIsTheLast30DaysAndTodayMatchesTheSummary() throws Exception {
		ingest(
				ev("traveler.active", "traveler-1", "2026-09-24T01:00:00Z"),
				ev("traveler.active", "traveler-2", "2026-09-24T02:00:00Z"));

		active("day", "UTC")
				.andExpect(jsonPath("$.points.length()").value(30))
				.andExpect(jsonPath("$.points[0].start").value("2026-08-26"))
				.andExpect(jsonPath("$.points[29].start").value("2026-09-24"))
				.andExpect(jsonPath("$.points[29].active").value(2));
		mockMvc.perform(get("/api/dashboard/summary").param("zone", "UTC")
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(jsonPath("$.activeToday").value(2));
	}

	@Test
	void anEventAt2330UtcIsTheNextDayInManila() throws Exception {
		ingest(ev("traveler.active", "traveler-1", "2026-09-23T23:30:00Z"));

		active("day", "Asia/Manila", "2026-09-23", "2026-09-24")
				.andExpect(jsonPath("$.points[0].active").value(0))
				.andExpect(jsonPath("$.points[1].active").value(1));
		active("day", "UTC", "2026-09-23", "2026-09-24")
				.andExpect(jsonPath("$.points[0].active").value(1))
				.andExpect(jsonPath("$.points[1].active").value(0));
	}

	@Test
	void aMonthCountsATravelerOnceHoweverManyDaysTheyWereActive() throws Exception {
		ingest(
				ev("traveler.active", "traveler-1", "2026-09-01T09:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-09-15T09:00:00Z"),
				ev("traveler.active", "traveler-2", "2026-09-20T09:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-08-20T09:00:00Z"));

		active("month", "UTC")
				.andExpect(jsonPath("$.points.length()").value(12))
				.andExpect(jsonPath("$.points[10].start").value("2026-08"))
				.andExpect(jsonPath("$.points[10].active").value(1))
				.andExpect(jsonPath("$.points[11].start").value("2026-09"))
				.andExpect(jsonPath("$.points[11].active").value(2));
	}

	@Test
	void theDefaultYearRangeIsEveryYearWithATraveler() throws Exception {
		ingest(
				ev("traveler.active", "traveler-1", "2025-03-01T09:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-03-01T09:00:00Z"),
				ev("traveler.active", "traveler-2", "2026-04-01T09:00:00Z"));

		active("year", "UTC")
				.andExpect(jsonPath("$.points.length()").value(2))
				.andExpect(jsonPath("$.points[0].start").value("2025"))
				.andExpect(jsonPath("$.points[0].active").value(1))
				.andExpect(jsonPath("$.points[1].start").value("2026"))
				.andExpect(jsonPath("$.points[1].active").value(2));
	}

	@Test
	void anInvalidZoneOrBucketIs400() throws Exception {
		active("day", "Not/AZone")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.zone").exists());
		active("week", "UTC")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.bucket").exists());
	}

	@Test
	void noTokenOrTheIntakeSecretIs401() throws Exception {
		mockMvc.perform(get("/api/dashboard/active").param("bucket", "day"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/dashboard/active").param("bucket", "day")
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isUnauthorized());
	}

	// ---- helpers -----------------------------------------------------------------------

	private ResultActions active(String bucket, String zone, String... fromTo) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/dashboard/active")
				.header("Authorization", "Bearer " + memberToken());
		if (bucket != null) request.param("bucket", bucket);
		if (zone != null) request.param("zone", zone);
		if (fromTo.length > 0) request.param("from", fromTo[0]);
		if (fromTo.length > 1) request.param("to", fromTo[1]);
		return mockMvc.perform(request);
	}

	private void ingest(String... events) throws Exception {
		mockMvc.perform(post("/api/intake/events")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Intake-Secret", TEST_INTAKE_SECRET)
						.content(batch(events)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[?(@.status != 'accepted')]").isEmpty());
	}

	private String memberToken() {
		var member = userRepository.findByUsername("member1").orElseThrow();
		return jwtService.issue(member.getId(), member.getRole());
	}
}
