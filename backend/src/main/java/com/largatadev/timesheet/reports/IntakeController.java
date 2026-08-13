package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The relay intake surface — the API's second and only other exception to bearer-JWT auth
 * (ADR-010). Authentication is the shared secret checked by {@link IntakeSecretFilter} before
 * this controller is reached; nothing here is reachable by a browser or by a Member's token.
 */
@RestController
@RequestMapping("/api/intake/reports")
public class IntakeController {

	private final ReportService reportService;
	private final ObjectMapper objectMapper;

	public IntakeController(ReportService reportService, ObjectMapper objectMapper) {
		this.reportService = reportService;
		this.objectMapper = objectMapper;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ReportResponse> intake(
			@RequestPart(name = "report", required = false) MultipartFile reportPart,
			@RequestPart(name = "screenshot", required = false) List<MultipartFile> screenshotParts) {

		ReportService.Intake result = reportService.accept(
				readPayload(reportPart), readScreenshots(screenshotParts));

		return ResponseEntity
				.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(result.report());
	}

	/** Image parts arrive in attachment order — that order becomes their ordinal. The declared
	 * content type is ignored; the service derives the stored one from the bytes. */
	private List<IncomingScreenshot> readScreenshots(List<MultipartFile> parts) {
		if (parts == null || parts.isEmpty()) {
			return List.of();
		}
		List<IncomingScreenshot> screenshots = new ArrayList<>(parts.size());
		for (MultipartFile part : parts) {
			try {
				byte[] bytes = part.getBytes();
				screenshots.add(new IncomingScreenshot(ReportService.sniffImageType(bytes), bytes));
			} catch (IOException unreadable) {
				throw new ValidationException("Invalid report",
						Map.of("screenshot", "could not be read"));
			}
		}
		return screenshots;
	}

	/**
	 * The JSON part is read by hand rather than bound with {@code @RequestPart IntakePayload}
	 * so that a malformed body from the other repo comes back as this API's own `400` envelope
	 * instead of a framework deserialization error.
	 */
	private IntakePayload readPayload(MultipartFile reportPart) {
		if (reportPart == null || reportPart.isEmpty()) {
			throw new ValidationException("Invalid report", Map.of("report", "part is required"));
		}
		try {
			return objectMapper.readValue(reportPart.getBytes(), IntakePayload.class);
		} catch (JacksonException | IOException malformed) {
			// Never echo the body back: it is unvalidated foreign input.
			throw new ValidationException("Invalid report", Map.of("report", "part must be valid JSON"));
		}
	}
}
