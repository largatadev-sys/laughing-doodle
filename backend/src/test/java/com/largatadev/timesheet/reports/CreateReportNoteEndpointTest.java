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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Notes log's create path and how notes reach the inbox (Story 20, ADR-012). A Note is the
 * team talking to its future self: authored by a Member, attributed from the JWT, and visible
 * only on the team routes — never on the relay's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreateReportNoteEndpointTest {

	private static final String TEST_INTAKE_SECRET = "create-note-test-shared-secret-value";

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
	ReportNoteRepository noteRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		// Notes first: they reference reports, and nothing cascades (the log is never deleted
		// in anger — only test fixtures go away).
		noteRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void addingANoteReturns201WithTheAuthorResolved() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();

		mockMvc.perform(noteRequest(report.getId(), "Reproduced on a Pixel 7. Fixing in the map layer.", "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.body").value("Reproduced on a Pixel 7. Fixing in the map layer."))
				.andExpect(jsonPath("$.authorId").value(member1.getId()))
				.andExpect(jsonPath("$.authorName").value("Member One"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				// Never edited, so no stamp — this absence is what the client renders against.
				.andExpect(jsonPath("$.editedBy").value(nullValue()))
				.andExpect(jsonPath("$.editedByName").value(nullValue()))
				.andExpect(jsonPath("$.editedAt").value(nullValue()));

		assertThat(noteRepository.findByReportIdOrderByCreatedAtAscIdAsc(report.getId()))
				.singleElement()
				.satisfies(note -> {
					assertThat(note.getAuthorId()).isEqualTo(member1.getId());
					assertThat(note.getEditedBy()).isNull();
				});
	}

	@Test
	void authorComesFromTheJwtEvenWhenTheBodySmugglesIdentity() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();

		// The body names member2 as author; the token belongs to member1. Same rule as every
		// other write in this API — identity is the token's, never the payload's.
		String spoofed = """
				{"body":"Whose note is this?","authorId":%d,"authorName":"Member Two","editedBy":%d}
				""".formatted(member2.getId(), member2.getId());

		mockMvc.perform(post("/api/reports/{id}/notes", report.getId())
						.header("Authorization", "Bearer " + tokenFor("member1"))
						.contentType("application/json")
						.content(spoofed))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.authorId").value(member1.getId()))
				.andExpect(jsonPath("$.authorName").value("Member One"))
				.andExpect(jsonPath("$.editedBy").value(nullValue()));

		assertThat(noteRepository.findAll())
				.singleElement()
				.satisfies(note -> assertThat(note.getAuthorId()).isEqualTo(member1.getId()));
	}

	@Test
	void notesComeBackEmbeddedOldestFirstOnTheInboxRead() throws Exception {
		Report report = save();

		// Written in this order by two different people — the log reads the way it was written.
		mockMvc.perform(noteRequest(report.getId(), "First: can't reproduce on iOS.", "member1"))
				.andExpect(status().isCreated());
		mockMvc.perform(noteRequest(report.getId(), "Second: Android only, tiles time out.", "member2"))
				.andExpect(status().isCreated());
		mockMvc.perform(noteRequest(report.getId(), "Third: shipping the retry in 1.4.3.", "member1"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].notes.length()").value(3))
				.andExpect(jsonPath("$[0].notes[0].body").value("First: can't reproduce on iOS."))
				.andExpect(jsonPath("$[0].notes[0].authorName").value("Member One"))
				.andExpect(jsonPath("$[0].notes[1].body").value("Second: Android only, tiles time out."))
				.andExpect(jsonPath("$[0].notes[1].authorName").value("Member Two"))
				.andExpect(jsonPath("$[0].notes[2].body").value("Third: shipping the retry in 1.4.3."));
	}

	@Test
	void aReportWithoutNotesEmbedsAnEmptyList() throws Exception {
		save();

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].notes").isArray())
				.andExpect(jsonPath("$[0].notes.length()").value(0));
	}

	@Test
	void notesFollowTheirOwnReportAndNoOther() throws Exception {
		// Distinct submission times so the list order is deterministic: newest first.
		Report annotated = save(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
		Report untouched = save(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));

		mockMvc.perform(noteRequest(annotated.getId(), "Only this one has a note.", "member1"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(annotated.getId().toString()))
				.andExpect(jsonPath("$[0].notes.length()").value(1))
				.andExpect(jsonPath("$[1].id").value(untouched.getId().toString()))
				.andExpect(jsonPath("$[1].notes.length()").value(0));
	}

	@Test
	void aStatusChangeStillCarriesTheReportsNotes() throws Exception {
		// The client swaps the returned report into its cached list, so a response that dropped
		// the notes would make them vanish from the screen until the next refetch.
		Report report = save();
		mockMvc.perform(noteRequest(report.getId(), "Parked until the founders decide.", "member1"))
				.andExpect(status().isCreated());

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.header("Authorization", "Bearer " + tokenFor("member2"))
						.contentType("application/json")
						.content("{\"status\":\"discuss\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("discuss"))
				.andExpect(jsonPath("$.notes.length()").value(1))
				.andExpect(jsonPath("$.notes[0].body").value("Parked until the founders decide."))
				.andExpect(jsonPath("$.notes[0].authorName").value("Member One"));
	}

	@Test
	void notesNeverLeakOntoTheIntakeSurface() throws Exception {
		// A replay of an already-annotated report must not hand Largata the team's own writing.
		UUID reportId = UUID.randomUUID();
		String json = IntakeEndpointTest.payload(reportId, "problem", "The trip map never loads.");

		mockMvc.perform(multipart("/api/intake/reports")
						.file(IntakeEndpointTest.reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		mockMvc.perform(noteRequest(reportId, "Internal: the founders want this dropped.", "member1"))
				.andExpect(status().isCreated());

		mockMvc.perform(multipart("/api/intake/reports")
						.file(IntakeEndpointTest.reportPart(json))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notes.length()").value(0));
	}

	@Test
	void aOneCharacterBodyIsAccepted() throws Exception {
		Report report = save();

		mockMvc.perform(noteRequest(report.getId(), "x", "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("x"));
	}

	@Test
	void aTwoThousandCharacterBodyIsAccepted() throws Exception {
		Report report = save();

		mockMvc.perform(noteRequest(report.getId(), "n".repeat(2000), "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("n".repeat(2000)));
	}

	@Test
	void anOverlongBodyReturns400AndPersistsNothing() throws Exception {
		Report report = save();

		mockMvc.perform(noteRequest(report.getId(), "n".repeat(2001), "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.body").exists());

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void anEmptyBodyReturns400() throws Exception {
		Report report = save();

		mockMvc.perform(noteRequest(report.getId(), "", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.body").exists());

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void aWhitespaceOnlyBodyReturns400() throws Exception {
		Report report = save();

		mockMvc.perform(noteRequest(report.getId(), "     ", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.body").exists());

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void aMissingBodyFieldReturns400() throws Exception {
		Report report = save();

		mockMvc.perform(post("/api/reports/{id}/notes", report.getId())
						.header("Authorization", "Bearer " + tokenFor("member1"))
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.body").exists());
	}

	@Test
	void unknownReportIdReturns404() throws Exception {
		mockMvc.perform(noteRequest(UUID.randomUUID(), "Into the void.", "member1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void noTokenReturns401AndPersistsNothing() throws Exception {
		Report report = save();

		mockMvc.perform(post("/api/reports/{id}/notes", report.getId())
						.contentType("application/json")
						.content("{\"body\":\"Unauthenticated.\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void invalidTokenReturns401AndPersistsNothing() throws Exception {
		Report report = save();

		mockMvc.perform(post("/api/reports/{id}/notes", report.getId())
						.header("Authorization", "Bearer not-a-real-token")
						.contentType("application/json")
						.content("{\"body\":\"Unauthenticated.\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(noteRepository.count()).isZero();
	}

	@Test
	void theIntakeSecretDoesNotOpenTheNotesRoute() throws Exception {
		// The two auth schemes still don't bleed: the relay's secret buys nothing here.
		Report report = save();

		mockMvc.perform(post("/api/reports/{id}/notes", report.getId())
						.header("X-Intake-Secret", TEST_INTAKE_SECRET)
						.contentType("application/json")
						.content("{\"body\":\"Relayed note?\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(noteRepository.count()).isZero();
	}

	private org.springframework.test.web.servlet.RequestBuilder noteRequest(
			UUID reportId, String body, String username) throws Exception {
		return post("/api/reports/{id}/notes", reportId)
				.header("Authorization", "Bearer " + tokenFor(username))
				.contentType("application/json")
				.content("{\"body\":\"%s\"}".formatted(body.replace("\\", "\\\\").replace("\"", "\\\"")));
	}

	private String tokenFor(String username) {
		User user = userRepository.findByUsername(username).orElseThrow();
		return jwtService.issue(user.getId(), user.getRole());
	}

	private Report save() {
		return save(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
	}

	private Report save(OffsetDateTime submittedAt) {
		return reportRepository.save(new Report(
				UUID.randomUUID(),
				ReportType.PROBLEM,
				"The trip map never loads on my phone.",
				"Ada Traveler",
				"largata-uid-1",
				Platform.ANDROID,
				"1.4.2",
				"(tabs)/(home)",
				submittedAt,
				OffsetDateTime.now(ZoneOffset.UTC)));
	}
}
