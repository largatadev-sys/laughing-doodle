package com.largatadev.timesheet.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.test.context.bean.override.convention.TestBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the Events intake route. These pin the wire contract the Largata repo
 * builds against (spec: docs/tickets/largata-dashboard/spec.md, "Wire contract") — treat a
 * change here as a cross-repo breaking change.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventIntakeEndpointTest {

	private static final String TEST_INTAKE_SECRET = "event-intake-endpoint-test-shared-secret";

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

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	/** Pinned so the occurredAt window ("2020 to one hour ahead") is testable. */
	static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

	@TestBean
	Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM largata_events");
	}

	@Test
	void aValidEventIsAcceptedAndStored() throws Exception {
		UUID id = UUID.randomUUID();

		send(batch(event(id, "trip.created", "traveler-1", "2026-09-24T10:00:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(1))
				.andExpect(jsonPath("$.results[0].eventId").value(id.toString()))
				.andExpect(jsonPath("$.results[0].status").value("accepted"))
				.andExpect(jsonPath("$.results[0].reason").doesNotExist());

		assertThat(storedKinds()).containsExactly("trip.created");
	}

	@Test
	void aReplayedBatchIsAllDuplicateAndStoresNothingNew() throws Exception {
		String body = batch(
				event(UUID.randomUUID(), "trip.created", "traveler-1", "2026-09-24T10:00:00Z"),
				event(UUID.randomUUID(), "postcard.created", "traveler-2", "2026-09-24T10:01:00Z"));

		send(body).andExpect(status().isOk());

		// A retry from Largata's outbox after a timeout, byte-identical.
		send(body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("duplicate"))
				.andExpect(jsonPath("$.results[1].status").value("duplicate"));

		assertThat(storedKinds()).containsExactly("trip.created", "postcard.created");
	}

	@Test
	void theSameEventIdTwiceInOneBatchIsStoredOnce() throws Exception {
		UUID id = UUID.randomUUID();
		String once = event(id, "diary.created", "traveler-1", "2026-09-24T10:00:00Z");

		send(batch(once, once))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"))
				.andExpect(jsonPath("$.results[1].status").value("duplicate"));

		assertThat(storedKinds()).hasSize(1);
	}

	@Test
	void aMixedBatchIsJudgedEventByEventInRequestOrderAndRejectsAreNotStored() throws Exception {
		UUID good = UUID.randomUUID();
		UUID noKind = UUID.randomUUID();
		UUID longKind = UUID.randomUUID();
		UUID longSubject = UUID.randomUUID();
		UUID noTime = UUID.randomUUID();
		UUID badTime = UUID.randomUUID();
		UUID badTotals = UUID.randomUUID();

		send(batch(
				"{\"eventId\":\"not-a-uuid\",\"kind\":\"trip.created\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				event(good, "trip.created", "traveler-1", "2026-09-24T10:00:00Z"),
				"{\"eventId\":\"" + noKind + "\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				event(longKind, "k".repeat(101), null, "2026-09-24T10:00:00Z"),
				event(longSubject, "trip.created", "s".repeat(201), "2026-09-24T10:00:00Z"),
				"{\"eventId\":\"" + noTime + "\",\"kind\":\"trip.created\"}",
				event(badTime, "trip.created", null, "yesterday-ish"),
				"{\"eventId\":\"" + badTotals + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\","
						+ "\"totals\":{\"trip\":-1}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(8))
				.andExpect(jsonPath("$.results[0].eventId").value("not-a-uuid"))
				.andExpect(jsonPath("$.results[0].status").value("rejected"))
				.andExpect(jsonPath("$.results[0].reason").value("eventId.invalid"))
				.andExpect(jsonPath("$.results[1].eventId").value(good.toString()))
				.andExpect(jsonPath("$.results[1].status").value("accepted"))
				.andExpect(jsonPath("$.results[2].reason").value("kind.missing"))
				.andExpect(jsonPath("$.results[3].reason").value("kind.tooLong"))
				.andExpect(jsonPath("$.results[4].reason").value("subject.tooLong"))
				.andExpect(jsonPath("$.results[5].reason").value("occurredAt.missing"))
				.andExpect(jsonPath("$.results[6].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[7].eventId").value(badTotals.toString()))
				.andExpect(jsonPath("$.results[7].status").value("rejected"))
				.andExpect(jsonPath("$.results[7].reason").value("totals.invalid"));

		assertThat(storedKinds()).containsExactly("trip.created");
	}

	@Test
	void boundaryLengthsAreAccepted() throws Exception {
		send(batch(event(UUID.randomUUID(), "k".repeat(100), "s".repeat(200), "2026-09-24T10:00:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));
	}

	@Test
	void anUnknownKindIsAcceptedLikeAnyOther() throws Exception {
		// Worklog validates the envelope, never the vocabulary.
		send(batch(event(UUID.randomUUID(), "souvenir.polished", "traveler-1", "2026-09-24T10:00:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));
	}

	@Test
	void aSnapshotIsStoredWithItsTotalsInTheOrderSent() throws Exception {
		send(batch("{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\","
				+ "\"occurredAt\":\"2026-09-24T10:00:00Z\","
				+ "\"totals\":{\"traveler\":12,\"trip\":30,\"postcard\":7}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));

		String stored = jdbc.queryForObject("SELECT totals::text FROM largata_events", String.class);
		assertThat(stored).isEqualTo("{\"traveler\":12,\"trip\":30,\"postcard\":7}");
	}

	@Test
	void aSnapshotWithNonIntegerOrMissingTotalsIsRejected() throws Exception {
		send(batch(
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\","
						+ "\"totals\":{\"trip\":1.5}}",
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\","
						+ "\"totals\":[1,2]}",
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].reason").value("totals.invalid"))
				.andExpect(jsonPath("$.results[1].reason").value("totals.invalid"))
				.andExpect(jsonPath("$.results[2].reason").value("totals.invalid"));

		assertThat(storedKinds()).isEmpty();
	}

	@Test
	void totalsOnAnyOtherKindAreIgnoredNotRejected() throws Exception {
		send(batch("{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"trip.created\","
				+ "\"occurredAt\":\"2026-09-24T10:00:00Z\",\"totals\":\"nonsense\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM largata_events WHERE totals IS NULL", Long.class))
				.isEqualTo(1);
	}

	@Test
	void aSlightlyFutureDatedEventIsAccepted() throws Exception {
		// Clock skew within the hour is trusted as sent.
		send(batch(event(UUID.randomUUID(), "trip.created", "traveler-1", "2026-09-24T12:59:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));
	}

	@Test
	void anOccurredAtOutsideTheWindowIsRejected() throws Exception {
		// The log is append-only: a far-future Snapshot would freeze every total and mute the
		// silent signal for good, and a sentinel date like 0001-01-01 would break the year
		// chart for good. So the window is 2020-01-01 to one hour after worklog's now.
		send(batch(
				event(UUID.randomUUID(), "trip.created", null, "2026-09-24T13:00:01Z"),
				event(UUID.randomUUID(), "trip.created", null, "2099-01-01T00:00:00Z"),
				event(UUID.randomUUID(), "trip.created", null, "2019-12-31T23:59:59Z"),
				event(UUID.randomUUID(), "trip.created", null, "0001-01-01T00:00:00Z"),
				event(UUID.randomUUID(), "trip.created", null, "+300000-01-01T00:00:00Z"),
				event(UUID.randomUUID(), "trip.created", null, "2020-01-01T00:00:00Z"),
				event(UUID.randomUUID(), "trip.created", null, "2026-09-24T13:00:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[1].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[2].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[3].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[4].reason").value("occurredAt.invalid"))
				.andExpect(jsonPath("$.results[5].status").value("accepted"))
				.andExpect(jsonPath("$.results[6].status").value("accepted"));

		assertThat(storedKinds()).hasSize(2);
	}

	@Test
	void controlCharactersAreRejectedPerEventAndNeverStored() throws Exception {
		// Postgres cannot store a NUL, and a NUL in a Snapshot key would make every later
		// summary fail; a batch must never 500 on one Event's content (the poison-pill rule).
		UUID good = UUID.randomUUID();
		send(batch(
				event(UUID.randomUUID(), "trip\\u0000.created", null, "2026-09-24T10:00:00Z"),
				event(UUID.randomUUID(), "trip.created", "traveler\\u0001", "2026-09-24T10:00:00Z"),
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\","
						+ "\"totals\":{\"tr\\u0000ip\":3}}",
				event(UUID.randomUUID(), "trip\\n.created", null, "2026-09-24T10:00:00Z"),
				event(good, "trip.created", "traveler-1", "2026-09-24T10:00:00Z")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].reason").value("kind.invalid"))
				.andExpect(jsonPath("$.results[1].reason").value("subject.invalid"))
				.andExpect(jsonPath("$.results[2].reason").value("totals.invalid"))
				.andExpect(jsonPath("$.results[3].reason").value("kind.invalid"))
				.andExpect(jsonPath("$.results[4].status").value("accepted"));

		assertThat(storedKinds()).containsExactly("trip.created");
	}

	@Test
	void theAmendedRulesHold() throws Exception {
		// spec Amendments (2026-09-24): wrong JSON types, canonical UUIDs, blank subject,
		// blank counter names, and a non-string eventId echoing null.
		UUID blankSubject = UUID.randomUUID();
		send(batch(
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":42,\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"trip.created\",\"subject\":7,\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				"{\"eventId\":\"1-1-1-1-1\",\"kind\":\"trip.created\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				"{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"snapshot\",\"occurredAt\":\"2026-09-24T10:00:00Z\",\"totals\":{\"\":3}}",
				"{\"eventId\":5,\"kind\":\"trip.created\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				"{\"eventId\":\"" + blankSubject + "\",\"kind\":\"trip.created\",\"subject\":\"  \",\"occurredAt\":\"2026-09-24T10:00:00Z\"}",
				"{\"eventId\":\"" + UUID.randomUUID().toString().toUpperCase() + "\",\"kind\":\"trip.created\",\"occurredAt\":\"2026-09-24T10:00:00Z\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].reason").value("kind.invalid"))
				.andExpect(jsonPath("$.results[1].reason").value("subject.invalid"))
				.andExpect(jsonPath("$.results[2].eventId").value("1-1-1-1-1"))
				.andExpect(jsonPath("$.results[2].reason").value("eventId.invalid"))
				.andExpect(jsonPath("$.results[3].reason").value("totals.invalid"))
				.andExpect(jsonPath("$.results[4].eventId").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.results[4].reason").value("eventId.invalid"))
				.andExpect(jsonPath("$.results[5].status").value("accepted"))
				.andExpect(jsonPath("$.results[6].status").value("accepted"));

		assertThat(jdbc.queryForObject("SELECT subject IS NULL FROM largata_events WHERE id = ?",
				Boolean.class, blankSubject)).isTrue();
		assertThat(storedKinds()).hasSize(2);
	}

	@Test
	void anOffsetTimestampIsAcceptedAndStoredAsTheSameInstant() throws Exception {
		send(batch(event(UUID.randomUUID(), "trip.created", null, "2026-09-24T18:00:00+08:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].status").value("accepted"));

		assertThat(jdbc.queryForObject(
				"SELECT occurred_at = TIMESTAMPTZ '2026-09-24T10:00:00Z' FROM largata_events", Boolean.class))
				.isTrue();
	}

	// ---- envelope-level 400s -------------------------------------------------------------

	@Test
	void anUnparseableBodyIs400WithTheFieldEnvelope() throws Exception {
		send("{\"events\": [")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.body").exists());
	}

	@Test
	void missingOrEmptyEventsIs400() throws Exception {
		send("{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.events").exists());
		send("{\"events\":[]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.events").exists());
		send("")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void moreThan500EventsIs400AndNothingIsStored() throws Exception {
		String[] events = new String[501];
		for (int i = 0; i < events.length; i++) {
			events[i] = event(UUID.randomUUID(), "trip.created", null, "2026-09-24T10:00:00Z");
		}

		send(batch(events))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.events").exists());

		assertThat(storedKinds()).isEmpty();
	}

	@Test
	void exactly500EventsIsAccepted() throws Exception {
		String[] events = new String[500];
		for (int i = 0; i < events.length; i++) {
			events[i] = event(UUID.randomUUID(), "trip.created", null, "2026-09-24T10:00:00Z");
		}

		send(batch(events))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(500));
	}

	// ---- security (mandatory, 06b) -------------------------------------------------------

	@Test
	void missingSecretIs401AndStoresNothing() throws Exception {
		mockMvc.perform(post("/api/intake/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(batch(event(UUID.randomUUID(), "trip.created", null, "2026-09-24T10:00:00Z"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(storedKinds()).isEmpty();
	}

	@Test
	void wrongSecretIs401AndStoresNothing() throws Exception {
		mockMvc.perform(post("/api/intake/events")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Intake-Secret", "not-the-secret")
						.content(batch(event(UUID.randomUUID(), "trip.created", null, "2026-09-24T10:00:00Z"))))
				.andExpect(status().isUnauthorized());

		assertThat(storedKinds()).isEmpty();
	}

	@Test
	void aValidMemberJwtDoesNotOpenEventIntake() throws Exception {
		mockMvc.perform(post("/api/intake/events")
						.contentType(MediaType.APPLICATION_JSON)
						.header("Authorization", "Bearer " + memberToken())
						.content(batch(event(UUID.randomUUID(), "trip.created", null, "2026-09-24T10:00:00Z"))))
				.andExpect(status().isUnauthorized());

		assertThat(storedKinds()).isEmpty();
	}

	// ---- helpers -----------------------------------------------------------------------

	@Autowired
	com.largatadev.timesheet.auth.JwtService jwtService;

	@Autowired
	com.largatadev.timesheet.users.UserRepository userRepository;

	private String memberToken() {
		var member = userRepository.findByUsername("member1").orElseThrow();
		return jwtService.issue(member.getId(), member.getRole());
	}

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(post("/api/intake/events")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Intake-Secret", TEST_INTAKE_SECRET)
				.content(body));
	}

	static String batch(String... events) {
		return "{\"events\":[" + String.join(",", events) + "]}";
	}

	static String event(UUID id, String kind, String subject, String occurredAt) {
		return "{\"eventId\":\"" + id + "\",\"kind\":\"" + kind + "\""
				+ (subject == null ? "" : ",\"subject\":\"" + subject + "\"")
				+ ",\"occurredAt\":\"" + occurredAt + "\"}";
	}

	private java.util.List<String> storedKinds() {
		return jdbc.queryForList("SELECT kind FROM largata_events ORDER BY occurred_at", String.class);
	}
}
