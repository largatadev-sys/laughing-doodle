package com.largatadev.timesheet.reports;

import com.largatadev.timesheet.error.ForbiddenException;
import com.largatadev.timesheet.error.NotFoundException;
import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

	static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A Note is a decision record, not a document — the same ceiling the reporter's own words
	 * get, so neither side of the screen can bury the other. */
	static final int NOTE_MAX_LENGTH = 2000;

	/** Generous for a route pattern or a human label; the cap is the only validation the
	 * field gets — its content is Largata's vocabulary, opaque here (contract v1.1). */
	static final int SCREEN_MAX_LENGTH = 200;

	/** Largata sends its sanitized, downsized display variant; anything larger is a mistake
	 * or an attack, not a screenshot. */
	static final int MAX_SCREENSHOTS = 3;
	static final long MAX_SCREENSHOT_BYTES = 5L * 1024 * 1024;

	private final ReportRepository reportRepository;
	private final ReportScreenshotRepository screenshotRepository;
	private final ReportNoteRepository noteRepository;
	private final ReportWriter reportWriter;
	private final UserRepository userRepository;

	public ReportService(
			ReportRepository reportRepository,
			ReportScreenshotRepository screenshotRepository,
			ReportNoteRepository noteRepository,
			ReportWriter reportWriter,
			UserRepository userRepository) {
		this.reportRepository = reportRepository;
		this.screenshotRepository = screenshotRepository;
		this.noteRepository = noteRepository;
		this.reportWriter = reportWriter;
		this.userRepository = userRepository;
	}

	/**
	 * Accept a relayed Report. Idempotent on the client-minted id: the first delivery inserts
	 * and reports {@code created}; any replay returns the stored Report untouched, so Largata's
	 * retry loop can be as aggressive as it likes.
	 *
	 * <p>Deliberately NOT wrapped in one transaction: the insert has to be able to fail on its
	 * own so the losing side of a primary-key race can still read the winner's row. Each
	 * repository call carries its own transaction, which is all this needs — there is no
	 * multi-write invariant to hold here.
	 */
	public Intake accept(IntakePayload payload, List<IncomingScreenshot> screenshots) {
		// Validate everything, images included, BEFORE the first write: a rejected attachment
		// must leave no report row behind (all-or-nothing).
		ParsedIntake parsed = parse(payload, screenshots);

		Optional<Report> existing = reportRepository.findById(parsed.id());
		if (existing.isPresent()) {
			// A replay never rewrites bytes — the stored screenshots are the ones that count.
			return new Intake(intakeResponse(existing.get()), false);
		}

		Report report = new Report(
				parsed.id(),
				parsed.type(),
				parsed.description(),
				parsed.reporterName(),
				parsed.reporterUid(),
				parsed.platform(),
				parsed.appVersion(),
				parsed.screen(),
				parsed.submittedAt(),
				OffsetDateTime.now());

		try {
			return new Intake(intakeResponse(reportWriter.insert(report, parsed.screenshots())), true);
		} catch (DataIntegrityViolationException raced) {
			// Two concurrent deliveries of the same id: this one lost the primary-key race,
			// which is exactly the idempotent outcome — return what the winner stored.
			// (The pre-check above is only an optimisation; the key is the real guarantee.)
			return new Intake(intakeResponse(reportRepository.findById(parsed.id())
					.orElseThrow(() -> raced)), false);
		}
	}

	@Transactional(readOnly = true)
	public List<ReportResponse> list(String statusFilter) {
		ReportStatus status = parseStatusFilter(statusFilter);
		List<Report> reports = reportRepository.findFiltered(status);
		List<UUID> reportIds = reports.stream().map(Report::getId).toList();

		Map<UUID, List<Integer>> ordinalsByReport = screenshotOrdinals(reportIds);
		// Every note for the whole page in one query, rather than one query per report.
		Map<UUID, List<ReportNote>> notesByReport = notesByReport(reportIds);
		Map<Long, String> namesById = resolveNames(reports, flatten(notesByReport));

		return reports.stream()
				.map(report -> ReportResponse.of(
						report,
						triagerName(report, namesById),
						ordinalsByReport.getOrDefault(report.getId(), List.of()),
						noteResponses(notesByReport.getOrDefault(report.getId(), List.of()), namesById)))
				.toList();
	}

	/**
	 * Append a Note. The author is the caller's JWT identity, passed in by the controller — the
	 * request body carries only text, so there is nothing here to spoof.
	 */
	@Transactional
	public ReportNoteResponse addNote(UUID reportId, String rawBody, Long authorId) {
		requireReport(reportId);
		String body = validateNoteBody(rawBody);

		ReportNote note = noteRepository.save(
				new ReportNote(reportId, authorId, body, OffsetDateTime.now()));
		return noteResponse(note);
	}

	/**
	 * Rewrite your own Note's text. <strong>Author-only</strong> (ADR-012, revised 2026-08-29
	 * after the developer saw the attributed ledger live): a Note is signed testimony from one
	 * Member, so nobody else may put words under their name. This is the same ownership shape
	 * as INV-2 on time entries — one rule across the app, not two.
	 *
	 * <p>Nothing removes the note, and its author and createdAt never move — the log itself
	 * stays append-only.
	 */
	@Transactional
	public ReportNoteResponse editNote(UUID reportId, UUID noteId, String rawBody, Long editorId) {
		requireReport(reportId);
		ReportNote note = noteRepository.findByIdAndReportId(noteId, reportId)
				.orElseThrow(() -> new NotFoundException("Note not found"));

		// 403, not 404: the note is real and readable — the caller simply doesn't own it. Same
		// status and wording shape as the entries service, so the API has one ownership answer.
		if (!note.getAuthorId().equals(editorId)) {
			throw new ForbiddenException("Only the author may edit this note");
		}

		String body = validateNoteBody(rawBody);

		note.edit(body, editorId, OffsetDateTime.now());
		return noteResponse(noteRepository.save(note));
	}

	/** Stream one screenshot's bytes to an authenticated Member. */
	@Transactional(readOnly = true)
	public ReportScreenshot screenshot(UUID reportId, int ordinal) {
		return screenshotRepository.findOne(reportId, ordinal)
				.orElseThrow(() -> new NotFoundException("Screenshot not found"));
	}

	@Transactional
	public ReportResponse changeStatus(UUID reportId, String requestedStatus, Long callerId) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new NotFoundException("Report not found"));

		ReportStatus status = parseEnum(ReportStatus.class, requestedStatus)
				.orElseThrow(() -> new ValidationException("Invalid status",
						Map.of("status", "must be one of: new, discuss, in_progress, done, dismissed")));

		// Attribution comes from the JWT identity the controller passed in — never the body.
		report.changeStatus(status, callerId, OffsetDateTime.now());
		return teamResponse(reportRepository.save(report));
	}

	/**
	 * The relay's view of a Report: no notes, ever, replay included. Largata relays feedback in;
	 * what the team wrote about it afterwards is not its business, and a triaged report's replay
	 * must not become a back-channel out.
	 */
	private ReportResponse intakeResponse(Report report) {
		return response(report, List.of());
	}

	/** A Member's view: the same Report with its notes attached. */
	private ReportResponse teamResponse(Report report) {
		return response(report, noteRepository.findByReportIdOrderByCreatedAtAscIdAsc(report.getId()));
	}

	private ReportResponse response(Report report, List<ReportNote> notes) {
		Map<Long, String> namesById = resolveNames(List.of(report), notes);
		List<Integer> ordinals = screenshotOrdinals(List.of(report.getId()))
				.getOrDefault(report.getId(), List.of());
		return ReportResponse.of(report, triagerName(report, namesById), ordinals,
				noteResponses(notes, namesById));
	}

	private ReportNoteResponse noteResponse(ReportNote note) {
		return noteResponses(List.of(note), resolveNames(List.of(), List.of(note))).getFirst();
	}

	private static List<ReportNoteResponse> noteResponses(List<ReportNote> notes, Map<Long, String> namesById) {
		return notes.stream()
				.map(note -> ReportNoteResponse.of(note,
						nameOf(namesById, note.getAuthorId()),
						nameOf(namesById, note.getEditedBy())))
				.toList();
	}

	/** Null-safe lookup: an unedited note has no editor id, and Map.of() throws on a null key. */
	private static String nameOf(Map<Long, String> namesById, Long userId) {
		return userId == null ? null : namesById.get(userId);
	}

	private Map<UUID, List<ReportNote>> notesByReport(List<UUID> reportIds) {
		if (reportIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<ReportNote>> byReport = new HashMap<>();
		for (ReportNote note : noteRepository.findByReportIdInOrderByCreatedAtAscIdAsc(reportIds)) {
			byReport.computeIfAbsent(note.getReportId(), key -> new ArrayList<>()).add(note);
		}
		return byReport;
	}

	private static List<ReportNote> flatten(Map<UUID, List<ReportNote>> notesByReport) {
		return notesByReport.values().stream().flatMap(List::stream).toList();
	}

	/** Every worklog User named anywhere on this page — triagers, note authors, note editors —
	 *  looked up once. The client holds no user directory, so names are resolved here. */
	private Map<Long, String> resolveNames(List<Report> reports, List<ReportNote> notes) {
		Set<Long> ids = new HashSet<>();
		reports.forEach(report -> ids.add(report.getStatusChangedBy()));
		notes.forEach(note -> {
			ids.add(note.getAuthorId());
			ids.add(note.getEditedBy());
		});
		// A never-triaged report and an unedited note both contribute a null here.
		ids.remove(null);

		if (ids.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, User::getName));
	}

	private void requireReport(UUID reportId) {
		if (!reportRepository.existsById(reportId)) {
			throw new NotFoundException("Report not found");
		}
	}

	private static String validateNoteBody(String rawBody) {
		String body = rawBody == null ? null : rawBody.trim();
		if (isBlank(body)) {
			throw new ValidationException("Invalid note", Map.of("body", "is required"));
		}
		if (body.length() > NOTE_MAX_LENGTH) {
			throw new ValidationException("Invalid note",
					Map.of("body", "must be at most " + NOTE_MAX_LENGTH + " characters"));
		}
		return body;
	}

	/** Which screenshot slots each report has — ordinals only, never the bytes. */
	private Map<UUID, List<Integer>> screenshotOrdinals(List<UUID> reportIds) {
		if (reportIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<Integer>> byReport = new HashMap<>();
		for (ReportScreenshotRepository.Slot slot : screenshotRepository.findOrdinalsByReportIds(reportIds)) {
			byReport.computeIfAbsent(slot.reportId(), key -> new ArrayList<>()).add(slot.ordinal());
		}
		return byReport;
	}

	/** Null for an untriaged Report — nobody has changed its status yet, so there is no one
	 * to attribute (and Map.of().get(null) would throw). */
	private static String triagerName(Report report, Map<Long, String> namesById) {
		Long triagerId = report.getStatusChangedBy();
		return triagerId == null ? null : namesById.get(triagerId);
	}

	private ReportStatus parseStatusFilter(String statusFilter) {
		if (statusFilter == null || statusFilter.isBlank()) {
			return null;
		}
		return parseEnum(ReportStatus.class, statusFilter)
				.orElseThrow(() -> new ValidationException("Invalid status filter",
						Map.of("status", "must be one of: new, discuss, in_progress, done, dismissed")));
	}

	/**
	 * Validate the whole foreign payload at once so Largata gets every problem in one `400`
	 * rather than one per round-trip.
	 */
	private ParsedIntake parse(IntakePayload payload, List<IncomingScreenshot> screenshots) {
		if (payload == null) {
			throw new ValidationException("Invalid report", Map.of("report", "part is required"));
		}

		Map<String, Object> details = new HashMap<>();
		validateScreenshots(screenshots, details);

		UUID id = null;
		if (isBlank(payload.reportId())) {
			details.put("reportId", "is required");
		} else {
			try {
				id = UUID.fromString(payload.reportId().trim());
			} catch (IllegalArgumentException e) {
				details.put("reportId", "must be a UUID");
			}
		}

		ReportType type = parseEnum(ReportType.class, payload.type()).orElse(null);
		if (type == null) {
			details.put("type", "must be one of: problem, idea");
		}

		String description = payload.description() == null ? null : payload.description().trim();
		if (isBlank(description)) {
			details.put("description", "is required");
		} else if (description.length() > DESCRIPTION_MAX_LENGTH) {
			details.put("description", "must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
		}

		// Contract v1.1: reporter identity is optional — a report from a signed-out Largata
		// screen has none to send. Name and uid are independently optional and stored as
		// sent: under store-and-forward a 400 is a silently lost report, so a half-sent
		// identity (a Largata bug, not the reporter's) must never cost the feedback.
		String reporterName = trimToNull(payload.reporter() == null ? null : payload.reporter().name());
		String reporterUid = trimToNull(payload.reporter() == null ? null : payload.reporter().uid());

		String rawPlatform = payload.context() == null ? null : payload.context().platform();
		Platform platform = parseEnum(Platform.class, rawPlatform).orElse(null);
		if (platform == null) {
			details.put("context.platform", "must be one of: android, ios, web");
		}

		String appVersion = payload.context() == null ? null : payload.context().appVersion();
		if (isBlank(appVersion)) {
			details.put("context.appVersion", "is required");
		}

		// Optional; length-capped and nothing else — the value is Largata's vocabulary
		// (route pattern or label, that repo's call), never validated against a route table.
		String screen = trimToNull(payload.context() == null ? null : payload.context().screen());
		if (screen != null && screen.length() > SCREEN_MAX_LENGTH) {
			details.put("context.screen", "must be at most " + SCREEN_MAX_LENGTH + " characters");
		}

		OffsetDateTime submittedAt = null;
		if (isBlank(payload.submittedAt())) {
			details.put("submittedAt", "is required");
		} else {
			try {
				submittedAt = OffsetDateTime.parse(payload.submittedAt().trim());
			} catch (DateTimeParseException e) {
				details.put("submittedAt", "must be an ISO-8601 instant");
			}
		}

		if (!details.isEmpty()) {
			throw new ValidationException("Invalid report", details);
		}

		return new ParsedIntake(id, type, description, reporterName, reporterUid,
				platform, appVersion.trim(), screen, submittedAt, screenshots);
	}

	/**
	 * Images are checked by their magic bytes, not by the part's declared content type: the
	 * declared type is caller-controlled, so trusting it would let anything be stored and later
	 * served back to a Member's browser as an image.
	 */
	private void validateScreenshots(List<IncomingScreenshot> screenshots, Map<String, Object> details) {
		if (screenshots.size() > MAX_SCREENSHOTS) {
			details.put("screenshot", "at most " + MAX_SCREENSHOTS + " screenshots are allowed");
			return;
		}

		for (IncomingScreenshot screenshot : screenshots) {
			byte[] bytes = screenshot.bytes();
			if (bytes.length == 0) {
				details.put("screenshot", "must not be empty");
				return;
			}
			if (bytes.length > MAX_SCREENSHOT_BYTES) {
				details.put("screenshot", "must be at most 5 MB");
				return;
			}
			if (sniffImageType(bytes) == null) {
				details.put("screenshot", "must be a JPEG or PNG image");
				return;
			}
		}
	}

	/** The stored content type, derived from the bytes themselves — null when it is neither. */
	static String sniffImageType(byte[] bytes) {
		if (bytes.length >= 8
				&& (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
				&& (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
				&& (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
			return "image/png";
		}
		if (bytes.length >= 3
				&& (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		return null;
	}

	private static <E extends Enum<E>> Optional<E> parseEnum(Class<E> enumType, String raw) {
		if (isBlank(raw)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Enum.valueOf(enumType, raw.trim().toUpperCase()));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String trimToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	/** An accepted Report plus whether this delivery is the one that created it (`201` vs `200`). */
	public record Intake(ReportResponse report, boolean created) {
	}

	private record ParsedIntake(UUID id, ReportType type, String description, String reporterName,
			String reporterUid, Platform platform, String appVersion, String screen,
			OffsetDateTime submittedAt, List<IncomingScreenshot> screenshots) {
	}
}
