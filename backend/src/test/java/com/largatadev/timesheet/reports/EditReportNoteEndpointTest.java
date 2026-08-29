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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editing a Note (Story 20, ADR-012 as revised 2026-08-29). <strong>Author-only</strong>: a Note
 * is signed testimony from one Member, so nobody else may put words under their name — the same
 * ownership rule INV-2 gives time entries. The log itself stays append-only: there is no delete
 * route, and an edit never moves the author or createdAt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EditReportNoteEndpointTest {

	private static final String TEST_INTAKE_SECRET = "edit-note-test-shared-secret-value";

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
		noteRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void anEditChangesTheBodyAndStampsTheEditorWhileAuthorshipHoldsStill() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Parked for now.");
		// Read back through the same path the assertion will use, so the comparison is about
		// the value not moving rather than about JDBC's timestamp precision.
		OffsetDateTime createdAt = noteRepository.findById(note.getId()).orElseThrow().getCreatedAt();

		mockMvc.perform(editRequest(report.getId(), note.getId(), "Parked until the pricing call.", "member1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(note.getId().toString()))
				.andExpect(jsonPath("$.body").value("Parked until the pricing call."))
				.andExpect(jsonPath("$.authorId").value(member1.getId()))
				.andExpect(jsonPath("$.authorName").value("Member One"))
				.andExpect(jsonPath("$.editedBy").value(member1.getId()))
				.andExpect(jsonPath("$.editedByName").value("Member One"))
				.andExpect(jsonPath("$.editedAt").isNotEmpty());

		ReportNote stored = noteRepository.findById(note.getId()).orElseThrow();
		assertThat(stored.getBody()).isEqualTo("Parked until the pricing call.");
		assertThat(stored.getAuthorId()).isEqualTo(member1.getId());
		assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void anotherMembersNoteCannotBeEdited() throws Exception {
		// The ownership rule, and the whole point of the revision: a Note is signed testimony.
		// member2 can read it, quote it, or write their own — they cannot rewrite member1's.
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Waiting on the founders.");

		mockMvc.perform(editRequest(report.getId(), note.getId(), "Founders said ship it.", "member2"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		ReportNote stored = noteRepository.findById(note.getId()).orElseThrow();
		assertThat(stored.getBody()).isEqualTo("Waiting on the founders.");
		assertThat(stored.getEditedBy()).isNull();
	}

	@Test
	void aRefusedEditIs403NotA404() throws Exception {
		// The distinction matters: 404 would imply the note isn't there, when in fact every
		// Member reads every note. It is present, readable, and simply not yours to rewrite —
		// the same answer editing another user's time entry gets.
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote mine = note(report, member1.getId(), "Mine.");

		mockMvc.perform(editRequest(report.getId(), mine.getId(), "Theirs now.", "member3"))
				.andExpect(status().isForbidden());

		// ...and the same caller can still SEE it on the inbox read.
		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].notes[0].body").value("Mine."));
	}

	@Test
	void aReEditReplacesTheStampRatherThanAccumulatingHistory() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "First wording.");

		mockMvc.perform(editRequest(report.getId(), note.getId(), "Second wording.", "member1"))
				.andExpect(status().isOk());

		mockMvc.perform(editRequest(report.getId(), note.getId(), "Third wording.", "member1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("Third wording."))
				// A note carries its LAST edit, not a revision list — "a changed decision gets
				// a new Note" is the convention, not a version history on this one.
				.andExpect(jsonPath("$.editedBy").value(member1.getId()))
				.andExpect(jsonPath("$.editedAt").isNotEmpty());

		assertThat(noteRepository.count()).isEqualTo(1);
	}

	@Test
	void theEditorStampIgnoresIdentityFieldsInTheBody() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Original.");

		// The body claims member2 edited it; the token belongs to member1.
		String spoofed = """
				{"body":"Rewritten.","editedBy":%d,"editedByName":"Member Two","authorId":%d}
				""".formatted(member2.getId(), member2.getId());

		mockMvc.perform(put("/api/reports/{id}/notes/{noteId}", report.getId(), note.getId())
						.header("Authorization", "Bearer " + tokenFor("member1"))
						.contentType("application/json")
						.content(spoofed))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.editedBy").value(member1.getId()))
				.andExpect(jsonPath("$.editedByName").value("Member One"))
				.andExpect(jsonPath("$.authorId").value(member1.getId()));
	}

	@Test
	void theEmbeddedNoteCarriesTheStampOnlyOnceEdited() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		note(report, member1.getId(), "Untouched.");
		ReportNote edited = note(report, member1.getId(), "Will be edited.");

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(jsonPath("$[0].notes[1].editedByName").value(nullValue()))
				.andExpect(jsonPath("$[0].notes[1].editedAt").value(nullValue()));

		mockMvc.perform(editRequest(report.getId(), edited.getId(), "Edited now.", "member1"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].notes[0].editedAt").value(nullValue()))
				.andExpect(jsonPath("$[0].notes[1].body").value("Edited now."))
				.andExpect(jsonPath("$[0].notes[1].editedByName").value("Member One"))
				.andExpect(jsonPath("$[0].notes[1].editedAt").isNotEmpty());
	}

	@Test
	void thereIsNoDeleteRouteAndTheNoteSurvivesTheAttempt() throws Exception {
		// Asserted, not assumed: append-only is a property of the routing table, so a change
		// that quietly adds a DELETE has to break a test.
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "This one is permanent.");

		mockMvc.perform(delete("/api/reports/{id}/notes/{noteId}", report.getId(), note.getId())
						.header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		assertThat(noteRepository.findById(note.getId())).isPresent();
	}

	@Test
	void unknownNoteIdReturns404() throws Exception {
		Report report = save();

		mockMvc.perform(editRequest(report.getId(), UUID.randomUUID(), "Nothing to edit.", "member1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void unknownReportIdReturns404() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Belongs elsewhere.");

		mockMvc.perform(editRequest(UUID.randomUUID(), note.getId(), "Wrong report.", "member1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void aNoteFromAnotherReportIsNotReachableThroughThisOne() throws Exception {
		Report mine = save();
		Report other = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(other, member1.getId(), "Lives on the other report.");

		mockMvc.perform(editRequest(mine.getId(), note.getId(), "Reached sideways?", "member1"))
				.andExpect(status().isNotFound());

		assertThat(noteRepository.findById(note.getId()).orElseThrow().getBody())
				.isEqualTo("Lives on the other report.");
	}

	@Test
	void anEmptyBodyReturns400AndLeavesTheNoteAlone() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Still here.");

		mockMvc.perform(editRequest(report.getId(), note.getId(), "", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.body").exists());

		ReportNote stored = noteRepository.findById(note.getId()).orElseThrow();
		assertThat(stored.getBody()).isEqualTo("Still here.");
		assertThat(stored.getEditedBy()).isNull();
	}

	@Test
	void anOverlongBodyReturns400AndLeavesTheNoteAlone() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Still here.");

		mockMvc.perform(editRequest(report.getId(), note.getId(), "n".repeat(2001), "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.body").exists());

		assertThat(noteRepository.findById(note.getId()).orElseThrow().getBody()).isEqualTo("Still here.");
	}

	@Test
	void noTokenReturns401AndTheNoteIsUnchanged() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Untouched by strangers.");

		mockMvc.perform(put("/api/reports/{id}/notes/{noteId}", report.getId(), note.getId())
						.contentType("application/json")
						.content("{\"body\":\"Rewritten by nobody.\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		ReportNote stored = noteRepository.findById(note.getId()).orElseThrow();
		assertThat(stored.getBody()).isEqualTo("Untouched by strangers.");
		assertThat(stored.getEditedBy()).isNull();
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Untouched by strangers.");

		mockMvc.perform(put("/api/reports/{id}/notes/{noteId}", report.getId(), note.getId())
						.header("Authorization", "Bearer not-a-real-token")
						.contentType("application/json")
						.content("{\"body\":\"Rewritten by nobody.\"}"))
				.andExpect(status().isUnauthorized());

		assertThat(noteRepository.findById(note.getId()).orElseThrow().getEditedBy()).isNull();
	}

	@Test
	void theIntakeSecretCannotEditANote() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		ReportNote note = note(report, member1.getId(), "Team-only writing.");

		mockMvc.perform(put("/api/reports/{id}/notes/{noteId}", report.getId(), note.getId())
						.header("X-Intake-Secret", TEST_INTAKE_SECRET)
						.contentType("application/json")
						.content("{\"body\":\"Relay rewrote this.\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(noteRepository.findById(note.getId()).orElseThrow().getBody())
				.isEqualTo("Team-only writing.");
	}

	private org.springframework.test.web.servlet.RequestBuilder editRequest(
			UUID reportId, UUID noteId, String body, String username) throws Exception {
		return put("/api/reports/{id}/notes/{noteId}", reportId, noteId)
				.header("Authorization", "Bearer " + tokenFor(username))
				.contentType("application/json")
				.content("{\"body\":\"%s\"}".formatted(body.replace("\\", "\\\\").replace("\"", "\\\"")));
	}

	private String tokenFor(String username) {
		User user = userRepository.findByUsername(username).orElseThrow();
		return jwtService.issue(user.getId(), user.getRole());
	}

	/** Seeded notes get distinct, increasing createdAt values so "oldest-first" is a real
	 *  assertion rather than a race between two calls to now(). */
	private int noteSeq = 0;

	private ReportNote note(Report report, Long authorId, String body) {
		OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).plusSeconds(noteSeq++);
		return noteRepository.saveAndFlush(new ReportNote(report.getId(), authorId, body, createdAt));
	}

	private Report save() {
		return reportRepository.save(new Report(
				UUID.randomUUID(),
				ReportType.PROBLEM,
				"The trip map never loads on my phone.",
				"Ada Traveler",
				"largata-uid-1",
				Platform.ANDROID,
				"1.4.2",
				"(tabs)/(home)",
				OffsetDateTime.now(ZoneOffset.UTC).minusHours(3),
				OffsetDateTime.now(ZoneOffset.UTC)));
	}
}
