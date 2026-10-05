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
 * Contract tests for the per-period counts (spec, "Aggregation rules" — series). The clock is
 * pinned at 2026-09-24T12:00Z, so the default ranges are known: the last 30 days end on
 * 2026-09-24 and the last 12 months on 2026-09.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardSeriesEndpointTest {

	static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
	private static final String TEST_INTAKE_SECRET = "dashboard-series-test-shared-secret";

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
	void aCounterThatNeverArrivedAnswersEmptyPoints() throws Exception {
		series("trip", "day", "UTC")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counter").value("trip"))
				.andExpect(jsonPath("$.bucket").value("day"))
				.andExpect(jsonPath("$.zone").value("UTC"))
				.andExpect(jsonPath("$.points.length()").value(0));
	}

	@Test
	void theDefaultDaySeriesIsTheLast30DaysWithEmptyDaysAsZeros() throws Exception {
		ingest(
				ev("trip.created", "t-1", "2026-09-20T08:00:00Z"),
				ev("trip.created", "t-2", "2026-09-20T09:00:00Z"),
				ev("trip.deleted", "t-2", "2026-09-22T09:00:00Z"),
				// Before the latest Snapshot: ignored by the total, still history in the series.
				ev("trip.created", "t-3", "2026-09-24T09:00:00Z"),
				snapshot("2026-09-24T10:00:00Z", "{\"trip\":2}"),
				// Other counters and other kinds never leak in.
				ev("postcard.created", "t-1", "2026-09-20T08:00:00Z"),
				ev("trip.archived", "t-1", "2026-09-20T08:00:00Z"),
				// Outside the default window.
				ev("trip.created", "t-1", "2026-08-25T23:00:00Z"));

		series("trip", "day", "UTC")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points.length()").value(30))
				.andExpect(jsonPath("$.points[0].start").value("2026-08-26"))
				.andExpect(jsonPath("$.points[0].created").value(0))
				.andExpect(jsonPath("$.points[0].deleted").value(0))
				.andExpect(jsonPath("$.points[25].start").value("2026-09-20"))
				.andExpect(jsonPath("$.points[25].created").value(2))
				.andExpect(jsonPath("$.points[25].deleted").value(0))
				.andExpect(jsonPath("$.points[26].created").value(0))
				.andExpect(jsonPath("$.points[27].start").value("2026-09-22"))
				.andExpect(jsonPath("$.points[27].deleted").value(1))
				.andExpect(jsonPath("$.points[29].start").value("2026-09-24"))
				.andExpect(jsonPath("$.points[29].created").value(1));
	}

	@Test
	void anEventAt2330UtcLandsOnTheNextDayInManila() throws Exception {
		ingest(ev("trip.created", "t-1", "2026-09-23T23:30:00Z"));

		series("trip", "day", "Asia/Manila", "2026-09-23", "2026-09-24")
				.andExpect(jsonPath("$.zone").value("Asia/Manila"))
				.andExpect(jsonPath("$.points.length()").value(2))
				.andExpect(jsonPath("$.points[0].start").value("2026-09-23"))
				.andExpect(jsonPath("$.points[0].created").value(0))
				.andExpect(jsonPath("$.points[1].start").value("2026-09-24"))
				.andExpect(jsonPath("$.points[1].created").value(1));

		series("trip", "day", "UTC", "2026-09-23", "2026-09-24")
				.andExpect(jsonPath("$.points[0].created").value(1))
				.andExpect(jsonPath("$.points[1].created").value(0));
	}

	@Test
	void theDefaultMonthSeriesIsTheLast12Months() throws Exception {
		ingest(
				ev("postcard.created", "t-1", "2025-10-01T00:00:00Z"),
				ev("postcard.created", "t-1", "2026-08-31T23:59:59Z"),
				ev("postcard.created", "t-1", "2026-09-01T00:00:00Z"),
				ev("postcard.deleted", "t-1", "2026-09-15T00:00:00Z"),
				ev("postcard.created", "t-1", "2025-09-30T23:59:59Z")); // outside

		series("postcard", "month", "UTC")
				.andExpect(jsonPath("$.bucket").value("month"))
				.andExpect(jsonPath("$.points.length()").value(12))
				.andExpect(jsonPath("$.points[0].start").value("2025-10"))
				.andExpect(jsonPath("$.points[0].created").value(1))
				.andExpect(jsonPath("$.points[1].created").value(0))
				.andExpect(jsonPath("$.points[10].start").value("2026-08"))
				.andExpect(jsonPath("$.points[10].created").value(1))
				.andExpect(jsonPath("$.points[11].start").value("2026-09"))
				.andExpect(jsonPath("$.points[11].created").value(1))
				.andExpect(jsonPath("$.points[11].deleted").value(1));
	}

	@Test
	void theMonthBoundaryFollowsTheZone() throws Exception {
		// 16:30 UTC on Aug 31 is 00:30 on Sep 1 in Manila.
		ingest(ev("postcard.created", "t-1", "2026-08-31T16:30:00Z"));

		series("postcard", "month", "Asia/Manila", "2026-08-01", "2026-09-30")
				.andExpect(jsonPath("$.points[0].start").value("2026-08"))
				.andExpect(jsonPath("$.points[0].created").value(0))
				.andExpect(jsonPath("$.points[1].start").value("2026-09"))
				.andExpect(jsonPath("$.points[1].created").value(1));
	}

	@Test
	void theDefaultYearSeriesIsEveryYearWithDataThroughThisOne() throws Exception {
		ingest(
				ev("diary.created", "t-1", "2024-06-01T00:00:00Z"),
				ev("diary.created", "t-1", "2026-01-01T00:00:00Z"),
				ev("diary.deleted", "t-1", "2026-02-01T00:00:00Z"));

		series("diary", "year", "UTC")
				.andExpect(jsonPath("$.bucket").value("year"))
				.andExpect(jsonPath("$.points.length()").value(3))
				.andExpect(jsonPath("$.points[0].start").value("2024"))
				.andExpect(jsonPath("$.points[0].created").value(1))
				.andExpect(jsonPath("$.points[1].start").value("2025"))
				.andExpect(jsonPath("$.points[1].created").value(0))
				.andExpect(jsonPath("$.points[2].start").value("2026"))
				.andExpect(jsonPath("$.points[2].created").value(1))
				.andExpect(jsonPath("$.points[2].deleted").value(1));
	}

	@Test
	void explicitFromAndToBoundTheRangeInclusively() throws Exception {
		ingest(
				ev("trip.created", "t-1", "2026-09-19T12:00:00Z"),
				ev("trip.created", "t-1", "2026-09-20T00:00:00Z"),
				ev("trip.created", "t-1", "2026-09-22T23:59:59Z"),
				ev("trip.created", "t-1", "2026-09-23T00:00:00Z"));

		series("trip", "day", "UTC", "2026-09-20", "2026-09-22")
				.andExpect(jsonPath("$.points.length()").value(3))
				.andExpect(jsonPath("$.points[0].start").value("2026-09-20"))
				.andExpect(jsonPath("$.points[0].created").value(1))
				.andExpect(jsonPath("$.points[2].start").value("2026-09-22"))
				.andExpect(jsonPath("$.points[2].created").value(1));
	}

	@Test
	void dayBucketsFollowTheZonesDaylightSavingChange() throws Exception {
		// DST ends in New York on Sunday 2026-11-01, so that day is 25 hours long. 04:30Z on
		// Nov 2 is 23:30 EST on Nov 1: correct bounds put it on Nov 1, naive 24h bounds on Nov 2.
		ingest(ev("trip.created", "t-1", "2026-09-20T12:00:00Z"));
		jdbc.update("INSERT INTO largata_events (id, kind, subject, occurred_at) VALUES (gen_random_uuid(), "
				+ "'trip.created', 't-1', TIMESTAMPTZ '2026-11-02T04:30:00Z')");

		series("trip", "day", "America/New_York", "2026-10-31", "2026-11-02")
				.andExpect(jsonPath("$.points.length()").value(3))
				.andExpect(jsonPath("$.points[1].start").value("2026-11-01"))
				.andExpect(jsonPath("$.points[1].created").value(1))
				.andExpect(jsonPath("$.points[2].created").value(0));
	}

	@Test
	void monthAndYearRangesCoverWholePeriods() throws Exception {
		// from/to pick the buckets they fall in; a bucket is always a whole month or year.
		ingest(
				ev("trip.created", "t-1", "2026-08-02T12:00:00Z"),
				ev("trip.created", "t-1", "2026-09-20T12:00:00Z"));

		series("trip", "month", "UTC", "2026-08-15", "2026-09-10")
				.andExpect(jsonPath("$.points.length()").value(2))
				.andExpect(jsonPath("$.points[0].start").value("2026-08"))
				.andExpect(jsonPath("$.points[0].created").value(1))
				.andExpect(jsonPath("$.points[1].created").value(1));
	}

	// ---- validation ----------------------------------------------------------------------

	@Test
	void datesOutsideASaneWindowAre400NotA500() throws Exception {
		ingest(ev("trip.created", "t-1", "2026-09-20T12:00:00Z"));

		series("trip", "day", "UTC", null, "+999999999-12-31")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.to").exists());
		series("trip", "year", "UTC", "+300000-01-01", "+300000-12-31")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.from").exists());
		series("trip", "day", "UTC", "1969-12-31", "1970-01-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.from").exists());
	}

	@Test
	void anInvalidZoneIs400WithKeyZoneAndAnAbsentOneIsUtc() throws Exception {
		series("trip", "day", "Not/AZone")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.zone").exists());
		series("trip", "day", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zone").value("UTC"));
	}

	@Test
	void aMissingCounterOrUnknownBucketIs400() throws Exception {
		series(null, "day", "UTC")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.counter").exists());
		series("trip", "week", "UTC")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.bucket").exists());
		series("trip", null, "UTC")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.bucket").exists());
	}

	@Test
	void badBoundsAre400() throws Exception {
		ingest(ev("trip.created", "t-1", "2026-09-20T12:00:00Z"));

		series("trip", "day", "UTC", "20-09-2026", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.from").exists());
		series("trip", "day", "UTC", "2026-09-22", "2026-09-20")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.from").exists());
		series("trip", "day", "UTC", "1990-01-01", "2026-09-20")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.from").exists());
	}

	// ---- security (mandatory, 06b) -------------------------------------------------------

	@Test
	void noOrInvalidTokenIs401() throws Exception {
		mockMvc.perform(get("/api/dashboard/series").param("counter", "trip").param("bucket", "day"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/dashboard/series").param("counter", "trip").param("bucket", "day")
						.header("Authorization", "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theIntakeSecretDoesNotOpenTheSeries() throws Exception {
		mockMvc.perform(get("/api/dashboard/series").param("counter", "trip").param("bucket", "day")
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isUnauthorized());
	}

	// ---- helpers -----------------------------------------------------------------------

	private ResultActions series(String counter, String bucket, String zone, String... fromTo) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/dashboard/series")
				.header("Authorization", "Bearer " + memberToken());
		if (counter != null) request.param("counter", counter);
		if (bucket != null) request.param("bucket", bucket);
		if (zone != null) request.param("zone", zone);
		if (fromTo.length > 0 && fromTo[0] != null) request.param("from", fromTo[0]);
		if (fromTo.length > 1 && fromTo[1] != null) request.param("to", fromTo[1]);
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
