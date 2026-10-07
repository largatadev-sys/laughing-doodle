package com.largatadev.timesheet.handoffs;

import com.largatadev.timesheet.auth.JwtService;
import com.largatadev.timesheet.reports.Platform;
import com.largatadev.timesheet.reports.Report;
import com.largatadev.timesheet.reports.ReportRepository;
import com.largatadev.timesheet.reports.ReportType;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating a Handoff (Story 28): a Member's frozen record of which Reports were passed on, by
 * whom, when, and the exact text. The creator is the JWT's; the text is stored as given.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreateHandoffEndpointTest {

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
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ReportRepository reportRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		// Children first: nothing cascades (Handoffs are never deleted — only fixtures go away).
		jdbc.update("DELETE FROM handoff_reports");
		jdbc.update("DELETE FROM handoffs");
		reportRepository.deleteAll();
	}

	@Test
	void creatingAHandoffReturns201WithTheFullHandoff() throws Exception {
		Report first = save();
		Report second = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();

		mockMvc.perform(create(List.of(second.getId().toString(), first.getId().toString()),
						"# Handoff · 2 reports\nBy Member One\n", "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.createdBy").value(member1.getId()))
				.andExpect(jsonPath("$.createdByName").value("Member One"))
				.andExpect(jsonPath("$.reportCount").value(2))
				.andExpect(jsonPath("$.text").value("# Handoff · 2 reports\nBy Member One\n"))
				// The order the Member's text numbers them in, not the order they were stored.
				.andExpect(jsonPath("$.reportIds[0]").value(second.getId().toString()))
				.andExpect(jsonPath("$.reportIds[1]").value(first.getId().toString()));
	}

	@Test
	void theCreatorComesFromTheJwtEvenWhenTheBodySmugglesIdentity() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();

		// The body names member2; the token is member1's. Identity is the token's, never the payload's.
		String spoofed = """
				{"reportIds":["%s"],"text":"Whose handoff?","createdBy":%d,"createdByName":"Member Two"}
				""".formatted(report.getId(), member2.getId());

		mockMvc.perform(createRaw(spoofed, "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdBy").value(member1.getId()))
				.andExpect(jsonPath("$.createdByName").value("Member One"));

		Long storedCreator = jdbc.queryForObject("SELECT created_by FROM handoffs", Long.class);
		assertThat(storedCreator).isEqualTo(member1.getId());
	}

	@Test
	void theTextIsStoredExactlyAsGiven() throws Exception {
		Report report = save();
		// Everything a trim or normaliser would touch: leading spaces, CRLF, tabs, backticks,
		// Markdown, emoji, blank lines and a trailing newline.
		String text = "  # Handoff · 2026-10-07 14:02 UTC · 1 report\r\nBy Ed\n\n"
				+ "## 1. c3d90b17 · problem\n\n> Shows `undefined`\t<b>here</b> 🐛\n>\n> \"quoted\" \\ end\n\n";

		mockMvc.perform(create(List.of(report.getId().toString()), text, "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.text").value(text));

		assertThat(jdbc.queryForObject("SELECT text FROM handoffs", String.class)).isEqualTo(text);
	}

	@Test
	void theSameReportMayBeHandedOffAgain() throws Exception {
		Report report = save();

		mockMvc.perform(create(List.of(report.getId().toString()), "first", "member1"))
				.andExpect(status().isCreated());
		mockMvc.perform(create(List.of(report.getId().toString()), "second", "member2"))
				.andExpect(status().isCreated());

		assertThat(handoffCount()).isEqualTo(2);
	}

	@Test
	void creatingAHandoffLeavesTheReportsStatusUntouched() throws Exception {
		Report report = save();

		mockMvc.perform(create(List.of(report.getId().toString()), "text", "member1"))
				.andExpect(status().isCreated());

		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(report.getStatus());
	}

	// ---- 400s ------------------------------------------------------------------------------------

	@Test
	void aMissingIdListReturns400() throws Exception {
		expect400(create(null, "text", "member1"), "reportIds");
	}

	@Test
	void anEmptyIdListReturns400() throws Exception {
		expect400(create(List.of(), "text", "member1"), "reportIds");
	}

	@Test
	void aDuplicateIdReturns400NamingIt() throws Exception {
		Report report = save();
		Report other = save();
		String id = report.getId().toString();

		mockMvc.perform(create(List.of(id, other.getId().toString(), id), "text", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.reportIds").exists())
				.andExpect(jsonPath("$.error.details.duplicateReportIds.length()").value(1))
				.andExpect(jsonPath("$.error.details.duplicateReportIds[0]").value(id));

		assertThat(handoffCount()).isZero();
	}

	@Test
	void anUnknownReportIdReturns400NamingIt() throws Exception {
		Report report = save();
		String unknown = UUID.randomUUID().toString();

		mockMvc.perform(create(List.of(report.getId().toString(), unknown), "text", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.reportIds").exists())
				.andExpect(jsonPath("$.error.details.unknownReportIds.length()").value(1))
				.andExpect(jsonPath("$.error.details.unknownReportIds[0]").value(unknown));

		assertThat(handoffCount()).isZero();
	}

	@Test
	void aMalformedReportIdReturns400NotA500() throws Exception {
		expect400(create(List.of("not-a-uuid"), "text", "member1"), "reportIds");
	}

	@Test
	void anIdListThatIsNotAListReturns400() throws Exception {
		expect400(create("c3d90b17", "text", "member1"), "reportIds");
	}

	@Test
	void malformedJsonReturns400NotA500() throws Exception {
		expect400(createRaw("{\"reportIds\": [", "member1"), "body");
	}

	@Test
	void moreThan200ReportsReturns400() throws Exception {
		// Unknown ids too, but the limit is judged before anything is looked up.
		List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 201; i++) {
			ids.add(UUID.randomUUID().toString());
		}
		mockMvc.perform(create(ids, "text", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.reportIds").value("must be at most 200 Reports"));
	}

	@Test
	void exactly200ReportsAreAccepted() throws Exception {
		List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 200; i++) {
			ids.add(save().getId().toString());
		}
		mockMvc.perform(create(ids, "text", "member1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reportCount").value(200))
				.andExpect(jsonPath("$.reportIds[199]").value(ids.get(199)));
	}

	@Test
	void aMissingTextReturns400() throws Exception {
		Report report = save();
		expect400(create(List.of(report.getId().toString()), null, "member1"), "text");
	}

	@Test
	void anEmptyTextReturns400() throws Exception {
		Report report = save();
		expect400(create(List.of(report.getId().toString()), "", "member1"), "text");
	}

	@Test
	void aBlankTextReturns400() throws Exception {
		Report report = save();
		expect400(create(List.of(report.getId().toString()), " \n\t ", "member1"), "text");
	}

	@Test
	void aTextOfExactlyAMillionCharactersIsAccepted() throws Exception {
		Report report = save();
		mockMvc.perform(create(List.of(report.getId().toString()), "x".repeat(1_000_000), "member1"))
				.andExpect(status().isCreated());
	}

	@Test
	void aTextOverAMillionCharactersReturns400() throws Exception {
		Report report = save();
		expect400(create(List.of(report.getId().toString()), "x".repeat(1_000_001), "member1"), "text");
	}

	@Test
	void aBodyOver8MbReturns400BeforeItIsParsed() throws Exception {
		Report report = save();
		// Valid JSON, so only the size check can reject it as "body" (the text rule would say "text").
		String json = "{\"reportIds\":[\"%s\"],\"text\":\"%s\"}"
				.formatted(report.getId(), "x".repeat(8 * 1024 * 1024));

		mockMvc.perform(createRaw(json, "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.body").value("must be at most 8 MB"))
				.andExpect(jsonPath("$.error.details.text").doesNotExist());
		assertThat(handoffCount()).isZero();
	}

	// ---- 401s ------------------------------------------------------------------------------------

	@Test
	void noTokenReturns401AndPersistsNothing() throws Exception {
		Report report = save();

		mockMvc.perform(post("/api/handoffs")
						.contentType("application/json")
						.content("{\"reportIds\":[\"%s\"],\"text\":\"t\"}".formatted(report.getId())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(handoffCount()).isZero();
	}

	@Test
	void anInvalidTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/handoffs")
						.header("Authorization", "Bearer not-a-real-token")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// ---- helpers --------------------------------------------------------------------------------

	private void expect400(RequestBuilder request, String field) throws Exception {
		mockMvc.perform(request)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details." + field).exists());
		assertThat(handoffCount()).isZero();
	}

	private RequestBuilder create(Object reportIds, Object text, String username) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		if (reportIds != null) {
			body.put("reportIds", reportIds);
		}
		if (text != null) {
			body.put("text", text);
		}
		return createRaw(objectMapper.writeValueAsString(body), username);
	}

	private RequestBuilder createRaw(String json, String username) {
		return post("/api/handoffs")
				.header("Authorization", "Bearer " + tokenFor(username))
				.contentType("application/json")
				.content(json);
	}

	private String tokenFor(String username) {
		User user = userRepository.findByUsername(username).orElseThrow();
		return jwtService.issue(user.getId(), user.getRole());
	}

	private long handoffCount() {
		Long count = jdbc.queryForObject("SELECT count(*) FROM handoffs", Long.class);
		return count == null ? 0 : count;
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
				null, null, null,
				OffsetDateTime.now(ZoneOffset.UTC).minusHours(3),
				OffsetDateTime.now(ZoneOffset.UTC)));
	}
}
