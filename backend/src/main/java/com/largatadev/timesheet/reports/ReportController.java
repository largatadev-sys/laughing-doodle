package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	/**
	 * Append a Note to the Report's log. There is a POST and a PUT here and deliberately no
	 * DELETE: the log is append-only by the absence of a route, not by a check someone could
	 * later relax (ADR-012).
	 */
	@PostMapping("/{id}/notes")
	public ResponseEntity<ReportNoteResponse> addNote(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable UUID id,
			@RequestBody(required = false) NoteRequest request) {

		String body = request == null ? null : request.body();
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(reportService.addNote(id, body, authenticatedUser.userId()));
	}

	/** Rewrite your own Note's text — author-only (ADR-012 as revised), the same ownership rule
	 * time entries obey. Someone else's note is a `403`, never a silent no-op. */
	@PutMapping("/{id}/notes/{noteId}")
	public ResponseEntity<ReportNoteResponse> editNote(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable UUID id,
			@PathVariable UUID noteId,
			@RequestBody(required = false) NoteRequest request) {

		String body = request == null ? null : request.body();
		return ResponseEntity.ok(reportService.editNote(id, noteId, body, authenticatedUser.userId()));
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
