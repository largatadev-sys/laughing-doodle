package com.largatadev.timesheet.reports;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

	static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** Largata sends its sanitized, downsized display variant; anything larger is a mistake
	 * or an attack, not a screenshot. */
	static final int MAX_SCREENSHOTS = 3;
	static final long MAX_SCREENSHOT_BYTES = 5L * 1024 * 1024;

	private final ReportRepository reportRepository;
	private final ReportScreenshotRepository screenshotRepository;
	private final ReportWriter reportWriter;
	private final UserRepository userRepository;

	public ReportService(
			ReportRepository reportRepository,
			ReportScreenshotRepository screenshotRepository,
			ReportWriter reportWriter,
			UserRepository userRepository) {
		this.reportRepository = reportRepository;
		this.screenshotRepository = screenshotRepository;
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
			return new Intake(toResponse(existing.get()), false);
		}

		Report report = new Report(
				parsed.id(),
				parsed.type(),
				parsed.description(),
				parsed.reporterName(),
				parsed.reporterUid(),
				parsed.platform(),
				parsed.appVersion(),
				parsed.submittedAt(),
				OffsetDateTime.now());

		try {
			return new Intake(toResponse(reportWriter.insert(report, parsed.screenshots())), true);
		} catch (DataIntegrityViolationException raced) {
			// Two concurrent deliveries of the same id: this one lost the primary-key race,
			// which is exactly the idempotent outcome — return what the winner stored.
			// (The pre-check above is only an optimisation; the key is the real guarantee.)
			return new Intake(toResponse(reportRepository.findById(parsed.id())
					.orElseThrow(() -> raced)), false);
		}
	}

	@Transactional(readOnly = true)
	public List<ReportResponse> list(String statusFilter) {
		ReportStatus status = parseStatusFilter(statusFilter);
		List<Report> reports = reportRepository.findFiltered(status);
		Map<Long, String> namesById = triagerNames(reports);

		Map<UUID, List<Integer>> ordinalsByReport = screenshotOrdinals(
				reports.stream().map(Report::getId).toList());

		return reports.stream()
				.map(report -> ReportResponse.of(
						report,
						triagerName(report, namesById),
						ordinalsByReport.getOrDefault(report.getId(), List.of())))
				.toList();
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
		return toResponse(reportRepository.save(report));
	}

	private ReportResponse toResponse(Report report) {
		String triagerName = report.getStatusChangedBy() == null
				? null
				: userRepository.findById(report.getStatusChangedBy()).map(User::getName).orElse(null);
		List<Integer> ordinals = screenshotOrdinals(List.of(report.getId()))
				.getOrDefault(report.getId(), List.of());
		return ReportResponse.of(report, triagerName, ordinals);
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

	private Map<Long, String> triagerNames(List<Report> reports) {
		List<Long> ids = reports.stream()
				.map(Report::getStatusChangedBy)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, User::getName));
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

		String reporterName = payload.reporter() == null ? null : payload.reporter().name();
		String reporterUid = payload.reporter() == null ? null : payload.reporter().uid();
		if (isBlank(reporterName)) {
			details.put("reporter.name", "is required");
		}
		if (isBlank(reporterUid)) {
			details.put("reporter.uid", "is required");
		}

		String rawPlatform = payload.context() == null ? null : payload.context().platform();
		Platform platform = parseEnum(Platform.class, rawPlatform).orElse(null);
		if (platform == null) {
			details.put("platform", "must be one of: android, ios, web");
		}

		String appVersion = payload.context() == null ? null : payload.context().appVersion();
		if (isBlank(appVersion)) {
			details.put("appVersion", "is required");
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

		return new ParsedIntake(id, type, description, reporterName.trim(), reporterUid.trim(),
				platform, appVersion.trim(), submittedAt, screenshots);
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

	/** An accepted Report plus whether this delivery is the one that created it (`201` vs `200`). */
	public record Intake(ReportResponse report, boolean created) {
	}

	private record ParsedIntake(UUID id, ReportType type, String description, String reporterName,
			String reporterUid, Platform platform, String appVersion, OffsetDateTime submittedAt,
			List<IncomingScreenshot> screenshots) {
	}
}
