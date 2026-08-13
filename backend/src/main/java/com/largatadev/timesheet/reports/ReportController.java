package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The team-facing inbox. Standard bearer-JWT surface — every Member sees every Report;
 * Reports have no owner, so there is no ownership check to make here. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping
	public ResponseEntity<List<ReportResponse>> list(@RequestParam(required = false) String status) {
		return ResponseEntity.ok(reportService.list(status));
	}

	/** Triage. Any Member may move any Report to any status — the attribution recorded is the
	 * caller's JWT identity, which is why the request body carries only the target status. */
	@PutMapping("/{id}/status")
	public ResponseEntity<ReportResponse> updateStatus(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable UUID id,
			@RequestBody(required = false) UpdateStatusRequest request) {

		String status = request == null ? null : request.status();
		return ResponseEntity.ok(reportService.changeStatus(id, status, authenticatedUser.userId()));
	}

	/** The image bytes, behind the same bearer JWT as everything else here — which is why the
	 * client fetches them rather than pointing an `img src` at this URL. */
	@GetMapping("/{id}/screenshots/{ordinal}")
	public ResponseEntity<byte[]> screenshot(@PathVariable UUID id, @PathVariable int ordinal) {
		ReportScreenshot screenshot = reportService.screenshot(id, ordinal);

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(screenshot.getContentType()))
				.body(screenshot.getBytes());
	}
}
