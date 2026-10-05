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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.largatadev.timesheet.dashboard.EventIntakeEndpointTest.batch;
import static com.largatadev.timesheet.dashboard.EventIntakeEndpointTest.event;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the Dashboard's headline numbers (spec, "Aggregation rules"). Events are
 * seeded through the intake route itself, so these also prove the two halves agree. The clock
 * is pinned at {@link #NOW} — 20:00 in Manila, noon in UTC — so "today" differs by zone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardSummaryEndpointTest {

	static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
	private static final String TEST_INTAKE_SECRET = "dashboard-summary-test-shared-secret";

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
	void anEmptyLogAnswersZerosAndSilent() throws Exception {
		summary("UTC")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.asOf").value("2026-09-24T12:00:00Z"))
				.andExpect(jsonPath("$.zone").value("UTC"))
				.andExpect(jsonPath("$.lastEventAt").value(nullValue()))
				.andExpect(jsonPath("$.lastSnapshotAt").value(nullValue()))
				.andExpect(jsonPath("$.silent").value(true))
				.andExpect(jsonPath("$.silentSince").value(nullValue()))
				.andExpect(jsonPath("$.activeToday").value(0))
				.andExpect(jsonPath("$.totals.length()").value(0));
	}

	@Test
	void aTotalIsTheSnapshotPlusCreatedMinusDeletedAfterIt() throws Exception {
		ingest(
				snapshot("2026-09-24T11:00:00Z", "{\"trip\":25}"),
				ev("trip.created", "t-1", "2026-09-24T11:10:00Z"),
				ev("trip.created", "t-2", "2026-09-24T11:20:00Z"),
				ev("trip.created", "t-3", "2026-09-24T11:30:00Z"),
				ev("trip.deleted", "t-3", "2026-09-24T11:40:00Z"));

		summary("UTC")
				.andExpect(jsonPath("$.totals.length()").value(1))
				.andExpect(jsonPath("$.totals[0].counter").value("trip"))
				.andExpect(jsonPath("$.totals[0].value").value(27))
				.andExpect(jsonPath("$.totals[0].baseline").value("2026-09-24T11:00:00Z"));
	}

	@Test
	void aCreatedDatedBeforeTheLatestSnapshotChangesNothing() throws Exception {
		// Late delivery: the Snapshot already counted this trip, so re-adding it would double it.
		ingest(
				snapshot("2026-09-24T11:00:00Z", "{\"trip\":25}"),
				ev("trip.created", "t-1", "2026-09-24T10:59:00Z"),
				ev("trip.created", "t-1", "2026-09-24T11:00:00Z"));

		summary("UTC").andExpect(jsonPath("$.totals[0].value").value(25));
	}

	@Test
	void aNewerSnapshotRebasesTheTotal() throws Exception {
		ingest(
				snapshot("2026-09-24T10:00:00Z", "{\"trip\":25}"),
				ev("trip.created", "t-1", "2026-09-24T10:30:00Z"),
				// Largata's own count says 30 — five more than worklog saw. The Snapshot wins.
				snapshot("2026-09-24T11:00:00Z", "{\"trip\":30}"),
				ev("trip.created", "t-1", "2026-09-24T11:30:00Z"));

		summary("UTC")
				.andExpect(jsonPath("$.totals[0].value").value(31))
				.andExpect(jsonPath("$.totals[0].baseline").value("2026-09-24T11:00:00Z"));
	}

	@Test
	void eachCounterRebasesOnTheLatestSnapshotThatCarriesIt() throws Exception {
		// The latest Snapshot stops reporting postcard; postcard keeps the baseline it last had.
		ingest(
				snapshot("2026-09-24T10:00:00Z", "{\"trip\":25,\"postcard\":5}"),
				ev("postcard.created", "t-1", "2026-09-24T10:30:00Z"),
				snapshot("2026-09-24T11:00:00Z", "{\"trip\":30}"),
				ev("postcard.created", "t-1", "2026-09-24T11:30:00Z"),
				ev("trip.created", "t-1", "2026-09-24T10:30:00Z")); // before trip's baseline: ignored

		summary("UTC")
				.andExpect(jsonPath("$.totals.length()").value(2))
				.andExpect(jsonPath("$.totals[0].counter").value("trip"))
				.andExpect(jsonPath("$.totals[0].value").value(30))
				.andExpect(jsonPath("$.totals[0].baseline").value("2026-09-24T11:00:00Z"))
				.andExpect(jsonPath("$.totals[1].counter").value("postcard"))
				.andExpect(jsonPath("$.totals[1].value").value(7))
				.andExpect(jsonPath("$.totals[1].baseline").value("2026-09-24T10:00:00Z"));
	}

	@Test
	void aCounterNoSnapshotCarriesHasNoBaseline() throws Exception {
		ingest(
				ev("souvenir.created", "t-1", "2026-09-20T10:00:00Z"),
				ev("souvenir.created", "t-2", "2026-09-21T10:00:00Z"),
				ev("souvenir.created", "t-2", "2026-09-22T10:00:00Z"),
				ev("souvenir.deleted", "t-2", "2026-09-23T10:00:00Z"));

		summary("UTC")
				.andExpect(jsonPath("$.totals[0].counter").value("souvenir"))
				.andExpect(jsonPath("$.totals[0].value").value(2))
				.andExpect(jsonPath("$.totals[0].baseline").value("none"));
	}

	@Test
	void totalsFollowTheLatestSnapshotsOrderThenTheRestAlphabetically() throws Exception {
		ingest(
				snapshot("2026-09-24T11:00:00Z", "{\"traveler\":40,\"trip\":25,\"postcard\":60,\"diary\":18}"),
				ev("zebra.created", "t-1", "2026-09-24T11:10:00Z"),
				ev("album.created", "t-1", "2026-09-24T11:10:00Z"),
				ev("traveler.active", "t-1", "2026-09-24T11:10:00Z"));

		summary("UTC")
				.andExpect(jsonPath("$.totals.length()").value(6))
				.andExpect(jsonPath("$.totals[0].counter").value("traveler"))
				.andExpect(jsonPath("$.totals[1].counter").value("trip"))
				.andExpect(jsonPath("$.totals[2].counter").value("postcard"))
				.andExpect(jsonPath("$.totals[3].counter").value("diary"))
				.andExpect(jsonPath("$.totals[4].counter").value("album"))
				.andExpect(jsonPath("$.totals[4].baseline").value("none"))
				.andExpect(jsonPath("$.totals[5].counter").value("zebra"));
	}

	@Test
	void aTravelerNamedByTwoKindsIsActiveOnce() throws Exception {
		ingest(
				ev("trip.created", "traveler-1", "2026-09-24T09:00:00Z"),
				ev("traveler.active", "traveler-1", "2026-09-24T10:00:00Z"),
				ev("postcard.created", "traveler-2", "2026-09-24T10:30:00Z"),
				// No subject: nobody did this, so it names no one.
				ev("trip.created", null, "2026-09-24T10:45:00Z"),
				// Yesterday in UTC.
				ev("traveler.active", "traveler-3", "2026-09-23T10:00:00Z"));

		summary("UTC").andExpect(jsonPath("$.activeToday").value(2));
	}

	@Test
	void anEventAt2330UtcIsTodayInManilaButYesterdayInUtc() throws Exception {
		// 23:30 UTC on the 23rd is 07:30 on the 24th in Manila (UTC+8) — the viewer's day.
		ingest(ev("traveler.active", "traveler-1", "2026-09-23T23:30:00Z"));

		summary("Asia/Manila")
				.andExpect(jsonPath("$.zone").value("Asia/Manila"))
				.andExpect(jsonPath("$.activeToday").value(1));
		summary("UTC").andExpect(jsonPath("$.activeToday").value(0));
	}

	@Test
	void freshnessReportsTheLatestEventAndSnapshot() throws Exception {
		ingest(
				snapshot("2026-09-24T11:00:00Z", "{\"trip\":1}"),
				ev("trip.created", "t-1", "2026-09-24T11:45:00Z"));

		summary("UTC")
				.andExpect(jsonPath("$.lastEventAt").value("2026-09-24T11:45:00Z"))
				.andExpect(jsonPath("$.lastSnapshotAt").value("2026-09-24T11:00:00Z"))
				.andExpect(jsonPath("$.silent").value(false))
				.andExpect(jsonPath("$.silentSince").value(nullValue()));
	}

	@Test
	void silentFlipsOnceTheLatestSnapshotIsOlderThanTwoHours() throws Exception {
		ingest(snapshot("2026-09-24T10:00:00Z", "{\"trip\":1}"));
		summary("UTC").andExpect(jsonPath("$.silent").value(false)); // exactly two hours old

		jdbc.update("DELETE FROM largata_events");
		ingest(
				snapshot("2026-09-24T09:59:00Z", "{\"trip\":1}"),
				// Events still flowing do not count as a heartbeat — only Snapshots do.
				ev("trip.created", "t-1", "2026-09-24T11:59:00Z"));
		summary("UTC")
				.andExpect(jsonPath("$.silent").value(true))
				.andExpect(jsonPath("$.silentSince").value("2026-09-24T09:59:00Z"));
	}

	@Test
	void anAbsentZoneMeansUtc() throws Exception {
		summary(null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zone").value("UTC"));
	}

	@Test
	void anInvalidZoneIs400WithKeyZone() throws Exception {
		summary("Mars/Olympus_Mons")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.zone").exists());
		summary("+08:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.zone").exists());
	}

	// ---- security (mandatory, 06b) -------------------------------------------------------

	@Test
	void noTokenIs401() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void anInvalidTokenIs401() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theIntakeSecretDoesNotOpenTheDashboard() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary").header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isUnauthorized());
	}

	// ---- helpers -----------------------------------------------------------------------

	private ResultActions summary(String zone) throws Exception {
		var request = get("/api/dashboard/summary").header("Authorization", "Bearer " + memberToken());
		if (zone != null) {
			request.param("zone", zone);
		}
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

	static String ev(String kind, String subject, String occurredAt) {
		return event(UUID.randomUUID(), kind, subject, occurredAt);
	}

	static String snapshot(String occurredAt, String totalsJson) {
		return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\""
				+ occurredAt + "\",\"totals\":" + totalsJson + "}";
	}

	private String memberToken() {
		var member = userRepository.findByUsername("member1").orElseThrow();
		return jwtService.issue(member.getId(), member.getRole());
	}
}
