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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UpdateReportStatusEndpointTest {

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
	void anyMemberMovesAnyReportToAnyStatus() throws Exception {
		Report report = save();

		// Free movement: no enforced transitions, so new → done is as legal as new → discuss.
		mockMvc.perform(statusRequest(report.getId(), "done", "member1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(report.getId().toString()))
				.andExpect(jsonPath("$.status").value("done"));

		mockMvc.perform(statusRequest(report.getId(), "new", "member2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("new"));

		mockMvc.perform(statusRequest(report.getId(), "dismissed", "member1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("dismissed"));
	}

	@Test
	void statusChangedByComesFromTheJwtNeverTheBody() throws Exception {
		Report report = save();
		User member1 = userRepository.findByUsername("member1").orElseThrow();
		User member2 = userRepository.findByUsername("member2").orElseThrow();

		// The body names member2 as the mover; the token belongs to member1. Attribution must
		// follow the token — the same identity rule as every write in this API.
		String spoofed = """
				{"status":"discuss","statusChangedBy":%d,"statusChangedByName":"Member Two"}
				""".formatted(member2.getId());

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.header("Authorization", "Bearer " + tokenFor("member1"))
						.contentType("application/json")
						.content(spoofed))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("discuss"))
				.andExpect(jsonPath("$.statusChangedBy").value(member1.getId()))
				.andExpect(jsonPath("$.statusChangedByName").value("Member One"))
				.andExpect(jsonPath("$.statusChangedAt").isNotEmpty());

		Report stored = reportRepository.findById(report.getId()).orElseThrow();
		assertThat(stored.getStatusChangedBy()).isEqualTo(member1.getId());
	}

	@Test
	void unknownStatusValueReturns400() throws Exception {
		Report report = save();

		mockMvc.perform(statusRequest(report.getId(), "archived", "member1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.status").exists());

		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.NEW);
	}

	@Test
	void missingStatusValueReturns400() throws Exception {
		Report report = save();

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.header("Authorization", "Bearer " + tokenFor("member1"))
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.status").exists());
	}

	@Test
	void unknownReportIdReturns404() throws Exception {
		mockMvc.perform(statusRequest(UUID.randomUUID(), "done", "member1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void noTokenReturns401AndChangesNothing() throws Exception {
		Report report = save();

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.contentType("application/json")
						.content("{\"status\":\"done\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.NEW);
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		Report report = save();

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.header("Authorization", "Bearer not-a-real-token")
						.contentType("application/json")
						.content("{\"status\":\"done\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void theIntakeSecretDoesNotOpenTheStatusRoute() throws Exception {
		Report report = save();

		mockMvc.perform(put("/api/reports/{id}/status", report.getId())
						.header("X-Intake-Secret", "any-value-at-all")
						.contentType("application/json")
						.content("{\"status\":\"done\"}"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.RequestBuilder statusRequest(
			UUID reportId, String status, String username) throws Exception {
		return put("/api/reports/{id}/status", reportId)
				.header("Authorization", "Bearer " + tokenFor(username))
				.contentType("application/json")
				.content("{\"status\":\"%s\"}".formatted(status));
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
				OffsetDateTime.now(ZoneOffset.UTC).minusHours(3),
				OffsetDateTime.now(ZoneOffset.UTC)));
	}
}
