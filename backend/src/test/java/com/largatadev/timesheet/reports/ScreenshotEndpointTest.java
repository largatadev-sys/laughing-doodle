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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static com.largatadev.timesheet.reports.IntakeEndpointTest.payload;
import static com.largatadev.timesheet.reports.IntakeEndpointTest.reportPart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Screenshots: the multipart half of the wire contract, storage, and authenticated serving. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScreenshotEndpointTest {

	private static final String TEST_INTAKE_SECRET = "screenshot-endpoint-test-shared-secret";

	// Real file signatures — the intake validates by magic bytes, not by the declared type, so
	// a test using arbitrary bytes would pass while a real JPEG failed (or vice versa).
	private static final byte[] PNG_BYTES = pngBytes();
	private static final byte[] JPEG_BYTES = jpegBytes();

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
	ReportScreenshotRepository screenshotRepository;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		screenshotRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void reportWithTwoImagesReturns201WithOrdinals() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Two screenshots attached.")))
						.file(imagePart("shot-a.png", "image/png", PNG_BYTES))
						.file(imagePart("shot-b.jpg", "image/jpeg", JPEG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.screenshotOrdinals.length()").value(2))
				.andExpect(jsonPath("$.screenshotOrdinals[0]").value(0))
				.andExpect(jsonPath("$.screenshotOrdinals[1]").value(1));

		assertThat(screenshotRepository.count()).isEqualTo(2);
	}

	@Test
	void screenshotStreamsBackByteIdenticalWithItsContentType() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Round-trip check.")))
						.file(imagePart("shot-a.png", "image/png", PNG_BYTES))
						.file(imagePart("shot-b.jpg", "image/jpeg", JPEG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		byte[] first = mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 0)
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andReturn().getResponse().getContentAsByteArray();

		byte[] second = mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 1)
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(first).isEqualTo(PNG_BYTES);
		assertThat(second).isEqualTo(JPEG_BYTES);
	}

	@Test
	void teamListCarriesOrdinalsButNeverBytes() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "idea", "A list check.")))
						.file(imagePart("shot-a.png", "image/png", PNG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		String body = mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].screenshotOrdinals.length()").value(1))
				.andReturn().getResponse().getContentAsString();

		// The list is a JSON index, not a transport for images: neither the bytes (in any
		// encoding) nor the stored content type appear anywhere in it.
		assertThat(body)
				.doesNotContain("image/png")
				.doesNotContain(java.util.Base64.getEncoder().encodeToString(PNG_BYTES))
				.doesNotContain("iVBORw0KGgo"); // the PNG signature, base64-encoded
	}

	@Test
	void moreThanThreeImagesReturns400AndPersistsNothing() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Four images.")))
						.file(imagePart("a.png", "image/png", PNG_BYTES))
						.file(imagePart("b.png", "image/png", PNG_BYTES))
						.file(imagePart("c.png", "image/png", PNG_BYTES))
						.file(imagePart("d.png", "image/png", PNG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.screenshot").exists());

		// All-or-nothing: the report row must not survive a rejected attachment either.
		assertThat(reportRepository.count()).isZero();
		assertThat(screenshotRepository.count()).isZero();
	}

	@Test
	void oversizedPartReturns400AndPersistsNothing() throws Exception {
		// Two distinct rejections have to hold, because in production only the FIRST one ever
		// fires and MockMvc can only exercise the second:
		//
		// 1. The servlet container refuses a >5MB part before any controller code runs. That
		//    surfaces as MaxUploadSizeExceededException, which without an explicit handler is
		//    a 500 — and Largata's store-and-forward loop ("retry until a 2xx") would then
		//    retry a permanently-bad payload forever. Asserted against the handler directly.
		// 2. The service's own size check, for any path that reaches it.
		var handled = new com.largatadev.timesheet.error.GlobalExceptionHandler()
				.handleMaxUploadSize(new MaxUploadSizeExceededException(ReportService.MAX_SCREENSHOT_BYTES));
		assertThat(handled.getStatusCode().value()).isEqualTo(400);
		assertThat(handled.getBody()).isNotNull();
		assertThat(handled.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(handled.getBody().error().details()).containsKey("screenshot");

		byte[] oversized = new byte[(int) ReportService.MAX_SCREENSHOT_BYTES + 1];
		System.arraycopy(PNG_BYTES, 0, oversized, 0, PNG_BYTES.length);

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "problem", "Oversized image.")))
						.file(imagePart("huge.png", "image/png", oversized))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.screenshot").exists());

		assertThat(reportRepository.count()).isZero();
		assertThat(screenshotRepository.count()).isZero();
	}

	@Test
	void nonImagePartReturns400AndPersistsNothing() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "A PDF sneaked in.")))
						.file(imagePart("notes.pdf", "application/pdf", "%PDF-1.7 not an image".getBytes()))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.details.screenshot").exists());

		assertThat(reportRepository.count()).isZero();
		assertThat(screenshotRepository.count()).isZero();
	}

	@Test
	void aPartClaimingToBePngButCarryingSomethingElseIsRejected() throws Exception {
		// The declared content type is attacker-controlled; the bytes decide.
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "problem", "Mislabelled part.")))
						.file(imagePart("evil.png", "image/png", "<html>not an image</html>".getBytes()))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details.screenshot").exists());

		assertThat(reportRepository.count()).isZero();
	}

	@Test
	void replayWithScreenshotsNeitherDuplicatesRowsNorRewritesBytes() throws Exception {
		UUID reportId = UUID.randomUUID();
		String json = payload(reportId, "problem", "Retried delivery with images.");

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.file(imagePart("a.png", "image/png", PNG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		// The retry carries DIFFERENT bytes for the same ordinal; the stored ones must win.
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(json))
						.file(imagePart("a.jpg", "image/jpeg", JPEG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.screenshotOrdinals.length()").value(1));

		assertThat(screenshotRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 0)
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"));
	}

	@Test
	void screenshotReadWithoutTokenReturns401() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Protected bytes.")))
						.file(imagePart("a.png", "image/png", PNG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 0))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 0)
						.header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownReportOrOrdinalReturns404() throws Exception {
		UUID reportId = UUID.randomUUID();

		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(reportId, "problem", "Only one image.")))
						.file(imagePart("a.png", "image/png", PNG_BYTES))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", UUID.randomUUID(), 0)
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/reports/{id}/screenshots/{ordinal}", reportId, 2)
						.header("Authorization", "Bearer " + memberToken()))
				.andExpect(status().isNotFound());
	}

	@Test
	void reportWithNoImagesStillWorksAndReportsNoOrdinals() throws Exception {
		mockMvc.perform(multipart("/api/intake/reports")
						.file(reportPart(payload(UUID.randomUUID(), "idea", "A quick suggestion, no images.")))
						.header("X-Intake-Secret", TEST_INTAKE_SECRET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.screenshotOrdinals.length()").value(0));
	}

	private static MockMultipartFile imagePart(String filename, String contentType, byte[] bytes) {
		return new MockMultipartFile("screenshot", filename, contentType, bytes);
	}

	private String memberToken() {
		User member = userRepository.findByUsername("member1").orElseThrow();
		return jwtService.issue(member.getId(), member.getRole());
	}

	/** A minimal but genuine 1×1 PNG (signature + IHDR/IDAT/IEND). */
	private static byte[] pngBytes() {
		return java.util.Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
	}

	/** A genuine 1×1 JPEG (SOI … EOI). */
	private static byte[] jpegBytes() {
		return java.util.Base64.getDecoder().decode(
				"/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");
	}
}
