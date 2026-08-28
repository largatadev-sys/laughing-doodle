package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.auth.JwtService;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the relay intake route. These pin the wire contract the Largata repo
 * builds against (spec: docs/tickets/reports-inbox/spec.md, "Wire contract") — treat a change
 * here as a cross-repo breaking change.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IntakeEndpointTest {

	private static final String TEST_INTAKE_SECRET = "intake-endpoint-test-shared-secret-value";

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
	void validSecretAndPayloadReturns201AndPersistsWithStatusNew() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "The trip map never loads on my phone.")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(reportId.toString()))
				.andExpect(jsonPath("$.type").value("problem"))
				.andExpect(jsonPath("$.description").value("The trip map never loads on my phone."))
				.andExpect(jsonPath("$.reporterName").value("Ada Traveler"))
				.andExpect(jsonPath("$.reporterUid").value("largata-uid-1"))
				.andExpect(jsonPath("$.platform").value("android"))
				.andExpect(jsonPath("$.appVersion").value("1.4.2"))
				.andExpect(jsonPath("$.screen").value("(tabs)/(trips)/itineraries/[id]"))
				.andExpect(jsonPath("$.status").value("new"))
				.andExpect(jsonPath("$.submittedAt").isNotEmpty())
				.andExpect(jsonPath("$.receivedAt").isNotEmpty())
				.andExpect(jsonPath("$.statusChangedBy").value(nullValue()))
				.andExpect(jsonPath("$.statusChangedAt").value(nullValue()));

		Report stored = reportRepository.findById(reportId).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(ReportStatus.NEW);
		assertThat(stored.getReceivedAt()).isNotNull();
	}

	@Test
	void replayOfSameReportIdReturns200AndCreatesNoSecondRow() throws Exception {
		UUID reportId = UUID.randomUUID();
		String json = payload(reportId, "idea", "Let me pin a hotel to a day.");

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		// A retry from Largata's store-and-forward outbox, byte-identical.
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(reportId.toString()))
				.andExpect(jsonPath("$.description").value("Let me pin a hotel to a day."));

		assertThat(reportRepository.count()).isEqualTo(1);
	}

	@Test
	void replayWithDifferentPayloadKeepsTheStoredReportUnchanged() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Original description.")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "idea", "Rewritten description.")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Original description."))
				.andExpect(jsonPath("$.type").value("problem"));
	}

	@Test
	void missingSecretReturns401AndPersistsNothing() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Should never land."))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void wrongSecretReturns401AndPersistsNothing() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Should never land.")))
						.header("X-Intake-Secret", "not-the-secret"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void aValidMemberJwtDoesNotOpenIntake() throws Exception {
		// The two auth schemes must not bleed: a team member's bearer token is not a relay secret.
		User member = userRepository.findByUsername("member1").orElseThrow();
		String token = jwtService.issue(member.getId(), member.getRole());

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "problem", "Member-authenticated attempt.")))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void unknownTypeReturns400WithFieldDetail() throws Exception {
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "complaint", "Not a known type.")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.type").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void blankDescriptionReturns400WithFieldDetail() throws Exception {
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "problem", "   ")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.description").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void overlongDescriptionReturns400WithFieldDetail() throws Exception {
		String tooLong = "x".repeat(2001);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "problem", tooLong)))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.description").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void exactly2000CharDescriptionIsAccepted() throws Exception {
		String atLimit = "y".repeat(2000);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "idea", atLimit)))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());
	}

	@Test
	void unknownPlatformReturns400WithFieldDetail() throws Exception {
		String json = """
				{"reportId":"%s","type":"problem","description":"Odd platform.",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"windows-phone","appVersion":"1.4.2"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details['context.platform']").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void missingReportIdReturns400WithFieldDetail() throws Exception {
		String json = """
				{"type":"problem","description":"No id at all.",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"ios","appVersion":"1.4.2"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""";

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.reportId").exists());
	}

	@Test
	void missingSubmittedAtReturns400WithFieldDetail() throws Exception {
		String json = """
				{"reportId":"%s","type":"idea","description":"No submittedAt.",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"web","appVersion":"1.4.2"}}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.submittedAt").exists());
	}

	@Test
	void signedOutReporterIsAcceptedWithNullIdentity() throws Exception {
		// v1.1: the tracker shows on signed-out Largata screens too — no reporter to send.
		UUID reportId = UUID.randomUUID();
		String json = """
				{"reportId":"%s","type":"problem","description":"The invite link did nothing.",
				 "context":{"platform":"web","appVersion":"1.4.2","screen":"join/[token]"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(reportId);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reporterName").value(nullValue()))
				.andExpect(jsonPath("$.reporterUid").value(nullValue()))
				.andExpect(jsonPath("$.screen").value("join/[token]"));

		Report stored = reportRepository.findById(reportId).orElseThrow();
		assertThat(stored.getReporterName()).isNull();
		assertThat(stored.getReporterUid()).isNull();
	}

	@Test
	void partialReporterIdentityIsStoredAsSent() throws Exception {
		// Name and uid are independently optional: a half-sent identity is a Largata bug,
		// and under store-and-forward a 400 here would silently lose the feedback.
		UUID reportId = UUID.randomUUID();
		String json = """
				{"reportId":"%s","type":"idea","description":"Half an identity.",
				 "reporter":{"name":"Ada Traveler"},
				 "context":{"platform":"web","appVersion":"1.4.2"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(reportId);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reporterName").value("Ada Traveler"))
				.andExpect(jsonPath("$.reporterUid").value(nullValue()));
	}

	@Test
	void missingScreenIsAcceptedAndStoredAsNull() throws Exception {
		// screen is optional forever: older Largata builds linger in the field, and a
		// required field would turn their reports into permanently retried 400s.
		UUID reportId = UUID.randomUUID();
		String json = """
				{"reportId":"%s","type":"idea","description":"Pre-v1.1 build shape.",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"android","appVersion":"1.4.2"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(reportId);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.screen").value(nullValue()));

		assertThat(reportRepository.findById(reportId).orElseThrow().getScreen()).isNull();
	}

	@Test
	void overlongScreenReturns400WithFieldDetail() throws Exception {
		String json = """
				{"reportId":"%s","type":"problem","description":"Screen too long.",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"android","appVersion":"1.4.2","screen":"%s"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(UUID.randomUUID(), "s".repeat(201));

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details['context.screen']").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void malformedJsonPartReturns400() throws Exception {
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart("{ this is not json"))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void missingReportPartReturns400() throws Exception {
		mockMvc.perform(multipart("/api/intake/reports")
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	static MockMultipartFile reportPart(String json) {
		return new MockMultipartFile("report", "report.json", "application/json",
				json.getBytes(StandardCharsets.UTF_8));
	}

	static String payload(UUID reportId, String type, String description) {
		// Hand-built rather than serialized from a DTO: this is the wire shape Largata sends,
		// so the test should break if the shape drifts, not follow it. (v1.1 shape: context
		// carries the optional screen.)
		return """
				{"reportId":"%s","type":"%s","description":"%s",
				 "reporter":{"name":"Ada Traveler","uid":"largata-uid-1"},
				 "context":{"platform":"android","appVersion":"1.4.2","screen":"(tabs)/(trips)/itineraries/[id]"},
				 "submittedAt":"2026-08-12T09:15:30Z"}
				""".formatted(reportId, type, description.replace("\\", "\\\\").replace("\"", "\\\""));
	}
}
