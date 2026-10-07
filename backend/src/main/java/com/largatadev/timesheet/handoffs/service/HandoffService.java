package com.largatadev.timesheet.handoffs.service;

import com.largatadev.timesheet.error.NotFoundException;
import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.handoffs.domain.Handoff;
import com.largatadev.timesheet.handoffs.dto.CreateHandoffRequest;
import com.largatadev.timesheet.handoffs.dto.HandoffResponse;
import com.largatadev.timesheet.handoffs.dto.HandoffSummaryResponse;
import com.largatadev.timesheet.handoffs.repository.HandoffRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.largatadev.timesheet.handoffs.repository.HandoffRepository.utc;

/**
 * Creates and reads Handoffs. A Handoff has no owner — any Member reads any Handoff — so the
 * only identity rule is that its creator is the caller, taken from the JWT by the controller.
 */
@Service
public class HandoffService {

	/** Far above any real Handoff; exists only so a client bug can't write an unbounded,
	 *  permanent row (spec, "Limits"). */
	static final int MAX_REPORTS = 200;
	/** In Java {@code String.length()} units (UTF-16), deliberately not enforced in the DB. */
	static final int MAX_TEXT_LENGTH = 1_000_000;

	private final HandoffRepository repository;
	private final Clock clock;

	HandoffService(HandoffRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public HandoffResponse create(CreateHandoffRequest request, long creatorId) {
		validate(request);
		// Truncated to Postgres's microsecond precision, so the response built from this object is
		// exactly what a later read of the stored row returns.
		Handoff handoff = new Handoff(UUID.randomUUID(), creatorId, clock.instant().truncatedTo(ChronoUnit.MICROS),
				request.text(), request.reportIds());
		repository.insert(handoff);
		return response(handoff, repository.nameOf(creatorId));
	}

	@Transactional(readOnly = true)
	public HandoffResponse get(UUID id) {
		return repository.findById(id)
				.map(stored -> response(stored.handoff(), stored.createdByName()))
				.orElseThrow(() -> new NotFoundException("Handoff not found"));
	}

	/**
	 * Every rule, reported together per field. The text is only ever judged, never altered: it
	 * is stored byte for byte, so blank is rejected with {@code isBlank()} and nothing is trimmed.
	 * Unknown ids are looked up only once the list is otherwise well-formed and within limits.
	 */
	private void validate(CreateHandoffRequest request) {
		Map<String, Object> details = new LinkedHashMap<>();

		List<UUID> ids = request.reportIds();
		if (ids == null || ids.isEmpty()) {
			details.put("reportIds", "is required");
		} else if (ids.size() > MAX_REPORTS) {
			details.put("reportIds", "must be at most " + MAX_REPORTS + " Reports");
		} else {
			List<UUID> duplicates = duplicatesOf(ids);
			if (!duplicates.isEmpty()) {
				details.put("reportIds", "must not repeat a Report");
				details.put("duplicateReportIds", duplicates);
			} else {
				Set<UUID> existing = repository.existingReportIds(ids);
				List<UUID> unknown = ids.stream().filter(id -> !existing.contains(id)).toList();
				if (!unknown.isEmpty()) {
					details.put("reportIds", "must name existing Reports");
					details.put("unknownReportIds", unknown);
				}
			}
		}

		String text = request.text();
		if (text == null || text.isBlank()) {
			details.put("text", "is required");
		} else if (text.length() > MAX_TEXT_LENGTH) {
			details.put("text", "must be at most " + MAX_TEXT_LENGTH + " characters");
		}

		if (!details.isEmpty()) {
			throw new ValidationException("Invalid handoff", details);
		}
	}

	private static List<UUID> duplicatesOf(List<UUID> ids) {
		Set<UUID> seen = new HashSet<>();
		Set<UUID> repeated = new LinkedHashSet<>();
		for (UUID id : ids) {
			if (!seen.add(id)) {
				repeated.add(id);
			}
		}
		return List.copyOf(repeated);
	}

	private static HandoffResponse response(Handoff handoff, String createdByName) {
		return new HandoffResponse(
				handoff.id(),
				utc(handoff.createdAt()),
				handoff.createdBy(),
				createdByName,
				handoff.reportIds().size(),
				handoff.text(),
				handoff.reportIds());
	}

	/** Every Handoff, newest first, as summaries — any Member sees all of them. */
	@Transactional(readOnly = true)
	public List<HandoffSummaryResponse> list() {
		return repository.findAllSummariesNewestFirst().stream()
				.map(summary -> new HandoffSummaryResponse(
						summary.id(),
						utc(summary.createdAt()),
						summary.createdBy(),
						summary.createdByName(),
						summary.reportCount()))
				.toList();
	}
}
