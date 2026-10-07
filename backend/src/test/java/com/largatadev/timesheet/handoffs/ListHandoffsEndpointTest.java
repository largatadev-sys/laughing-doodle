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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Handoffs list (Story 28, ticket 03): every Handoff, newest first, as summaries that never
 * carry the text — read by any Member.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListHandoffsEndpointTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	private static final String TEXT = "# Handoff · 2026-10-07 14:02 UTC · 1 report\nBy Member One\n";

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
		jdbc.update("DELETE FROM handoff_reports");
		jdbc.update("DELETE FROM handoffs");
		reportRepository.deleteAll();
	}

	@Test
	void everyHandoffComesBackNewestFirstAsASummaryWithoutTheText() throws Exception {
		Report a = save();
		Report b = save();
		Report c = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();

		String oldest = createHandoff(List.of(a.getId()), "member1");
		String newest = createHandoff(List.of(a.getId(), b.getId(), c.getId()), "member2");
		String middle = createHandoff(List.of(b.getId(), c.getId()), "member1");
		// Pin the times so the order is the test's, not the clock's resolution.
		createdAt(oldest, "2026-10-01T09:00:00Z");
		createdAt(middle, "2026-10-03T09:00:00Z");
		createdAt(newest, "2026-10-05T09:00:00Z");

		// Read by a Member who made none of them: a Handoff has no owner.
		mockMvc.perform(get("/api/handoffs").header("Authorization", "Bearer " + tokenFor("member3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(newest))
				.andExpect(jsonPath("$[0].createdAt").value("2026-10-05T09:00:00Z"))
				.andExpect(jsonPath("$[0].createdBy").value(member2.getId()))
				.andExpect(jsonPath("$[0].createdByName").value("Member Two"))
				.andExpect(jsonPath("$[0].reportCount").value(3))
				.andExpect(jsonPath("$[1].id").value(middle))
				.andExpect(jsonPath("$[1].createdBy").value(member1.getId()))
				.andExpect(jsonPath("$[1].createdByName").value("Member One"))
				.andExpect(jsonPath("$[1].reportCount").value(2))
				.andExpect(jsonPath("$[2].id").value(oldest))
				.andExpect(jsonPath("$[2].reportCount").value(1))
				// A summary is exactly these five fields: never the text, never the Report ids.
				.andExpect(jsonPath("$[0].length()").value(5))
				.andExpect(jsonPath("$[0].text").doesNotExist())
				.andExpect(jsonPath("$[0].reportIds").doesNotExist());
	}

	@Test
	void noHandoffsReturnsAnEmptyList() throws Exception {
		mockMvc.perform(get("/api/handoffs").header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/handoffs"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// ---- helpers --------------------------------------------------------------------------------

	private String createHandoff(List<UUID> reportIds, String username) throws Exception {
		String response = mockMvc.perform(post("/api/handoffs")
						.header("Authorization", "Bearer " + tokenFor(username))
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(Map.of("reportIds", reportIds, "text", TEXT))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		JsonNode created = objectMapper.readTree(response);
		return created.get("id").asString();
	}

	private void createdAt(String handoffId, String instant) {
		jdbc.update("UPDATE handoffs SET created_at = ? WHERE id = ?",
				OffsetDateTime.parse(instant), UUID.fromString(handoffId));
	}

	private String tokenFor(String username) {
		User user = userRepository.findByUsername(username).orElseThrow();
		return jwtService.issue(user.getId(), user.getRole());
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
