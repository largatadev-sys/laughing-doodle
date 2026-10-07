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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reading one Handoff back (Story 28): any Member, the text byte for byte, the Report ids in
 * order — and no route that could change or remove it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GetHandoffEndpointTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	private static final String TEXT = "# Handoff · 2026-10-07 14:02 UTC · 2 reports\r\nBy Member One\n\n"
			+ "## 1. c3d90b17 · problem\n\n> `undefined` 🐛\n>\n>   indented\n\n";

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
	void anyMemberReadsAHandoffInFull() throws Exception {
		Report first = save();
		Report second = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		JsonNode created = createHandoff(List.of(second.getId(), first.getId()), TEXT, "member1");

		// Read by someone else: a Handoff has no owner.
		mockMvc.perform(get("/api/handoffs/{id}", created.get("id").asString())
						.header("Authorization", "Bearer " + tokenFor("member2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.get("id").asString()))
				.andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asString()))
				.andExpect(jsonPath("$.createdBy").value(member1.getId()))
				.andExpect(jsonPath("$.createdByName").value("Member One"))
				.andExpect(jsonPath("$.reportCount").value(2))
				.andExpect(jsonPath("$.text").value(TEXT))
				.andExpect(jsonPath("$.reportIds.length()").value(2))
				.andExpect(jsonPath("$.reportIds[0]").value(second.getId().toString()))
				.andExpect(jsonPath("$.reportIds[1]").value(first.getId().toString()));
	}

	@Test
	void anUnknownHandoffReturns404() throws Exception {
		mockMvc.perform(get("/api/handoffs/{id}", UUID.randomUUID())
						.header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void aMalformedHandoffIdReturns400NotA500() throws Exception {
		mockMvc.perform(get("/api/handoffs/{id}", "not-a-uuid")
						.header("Authorization", "Bearer " + tokenFor("member1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void noTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/handoffs/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void thereIsNoEditOrDeleteRouteAndTheHandoffSurvivesTheAttempts() throws Exception {
		Report report = save();
		String id = createHandoff(List.of(report.getId()), TEXT, "member1").get("id").asString();
		String token = "Bearer " + tokenFor("member1");
		String body = "{\"text\":\"rewritten\"}";

		// Absent methods answer 404 per the existing convention (GlobalExceptionHandler).
		mockMvc.perform(put("/api/handoffs/{id}", id).header("Authorization", token)
						.contentType("application/json").content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		mockMvc.perform(patch("/api/handoffs/{id}", id).header("Authorization", token)
						.contentType("application/json").content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		mockMvc.perform(delete("/api/handoffs/{id}", id).header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/handoffs/{id}", id).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.text").value(TEXT));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM handoffs", Long.class)).isEqualTo(1);
	}

	// ---- helpers --------------------------------------------------------------------------------

	private JsonNode createHandoff(List<UUID> reportIds, String text, String username) throws Exception {
		String response = mockMvc.perform(post("/api/handoffs")
						.header("Authorization", "Bearer " + tokenFor(username))
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(Map.of("reportIds", reportIds, "text", text))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		return objectMapper.readTree(response);
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
