package com.largatadev.timesheet.dashboard.service;

import com.largatadev.timesheet.dashboard.dto.EventBatchResult;
import com.largatadev.timesheet.dashboard.dto.EventVerdict;
import com.largatadev.timesheet.dashboard.repository.LargataEventRepository;
import com.largatadev.timesheet.dashboard.domain.LargataEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Judges a batch of Events one by one (spec, "Wire contract"). Worklog validates the envelope
 * and never the vocabulary: a kind it has never seen is accepted like any other.
 *
 * <p>A bad Event is a {@code rejected} verdict, never an exception — Largata's outbox
 * dead-letters anything that gets a 4xx, so one malformed Event must not sink the batch
 * (the poison-pill rule). One transaction per batch: the accepted Events land together or,
 * on a database failure, not at all, and the replay answers correctly either way.
 */
@Service
public class EventIntakeService {

	private static final Logger log = LoggerFactory.getLogger(EventIntakeService.class);

	static final String SNAPSHOT = "snapshot";
	static final int KIND_MAX = 100;
	static final int SUBJECT_MAX = 200;

	/**
	 * The occurredAt window. The log is append-only, so a date outside it would do permanent
	 * harm: a far-future Snapshot becomes every counter's baseline (freezing all totals) and
	 * the latest heartbeat (muting "silent"); a sentinel date like 0001-01-01 stretches the
	 * year chart past its bucket cap. Largata stamps occurredAt on its own server, so an hour
	 * of skew is generous, and no Event predates Largata.
	 */
	static final Instant EARLIEST_OCCURRED_AT = Instant.parse("2020-01-01T00:00:00Z");
	static final Duration MAX_FUTURE_SKEW = Duration.ofHours(1);

	private final LargataEventRepository repository;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	EventIntakeService(LargataEventRepository repository, ObjectMapper objectMapper, Clock clock) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * The batch arrives as raw JSON trees on purpose: a mistyped field must become this
	 * Event's verdict, and binding to a typed DTO would fail the whole batch instead (the
	 * poison-pill rule above).
	 */
	@Transactional
	public EventBatchResult accept(List<JsonNode> events) {
		Instant latestAllowed = clock.instant().plus(MAX_FUTURE_SKEW);
		List<EventVerdict> verdicts = new ArrayList<>(events.size());
		for (JsonNode node : events) {
			verdicts.add(judge(node, latestAllowed));
		}
		logOutcome(verdicts);
		return new EventBatchResult(verdicts);
	}

	/**
	 * One line per batch, and one more when anything was rejected: a rejected Event is not
	 * stored, so this log is worklog's only trace of it. Counts and reasons only — never a
	 * subject (a Traveler's identity), totals or the body.
	 */
	private static void logOutcome(List<EventVerdict> verdicts) {
		Map<String, Long> byStatus = verdicts.stream()
				.collect(Collectors.groupingBy(EventVerdict::status, TreeMap::new, Collectors.counting()));
		log.info("Events batch: {} accepted, {} duplicate, {} rejected",
				byStatus.getOrDefault(EventVerdict.ACCEPTED, 0L),
				byStatus.getOrDefault(EventVerdict.DUPLICATE, 0L),
				byStatus.getOrDefault(EventVerdict.REJECTED, 0L));
		if (byStatus.containsKey(EventVerdict.REJECTED)) {
			Map<String, Long> byReason = verdicts.stream()
					.filter(v -> EventVerdict.REJECTED.equals(v.status()))
					.collect(Collectors.groupingBy(EventVerdict::reason, TreeMap::new, Collectors.counting()));
			log.warn("Events rejected by reason: {}", byReason);
		}
	}

	private EventVerdict judge(JsonNode node, Instant latestAllowed) {
		String rawId = stringOrNull(node.path("eventId"));
		UUID id = parseUuid(rawId);
		if (id == null) {
			return EventVerdict.rejected(rawId, "eventId.invalid");
		}

		JsonNode kindNode = node.path("kind");
		if (isAbsent(kindNode) || (kindNode.isString() && kindNode.stringValue().isBlank())) {
			return EventVerdict.rejected(rawId, "kind.missing");
		}
		if (!kindNode.isString()) {
			return EventVerdict.rejected(rawId, "kind.invalid");
		}
		String kind = kindNode.stringValue();
		if (hasControlCharacter(kind)) {
			return EventVerdict.rejected(rawId, "kind.invalid");
		}
		if (kind.length() > KIND_MAX) {
			return EventVerdict.rejected(rawId, "kind.tooLong");
		}

		JsonNode subjectNode = node.path("subject");
		String subject = null;
		if (!isAbsent(subjectNode)) {
			if (!subjectNode.isString() || hasControlCharacter(subjectNode.stringValue())) {
				return EventVerdict.rejected(rawId, "subject.invalid");
			}
			// A blank subject names nobody: stored as no Traveler, not as a Traveler named "".
			subject = subjectNode.stringValue().isBlank() ? null : subjectNode.stringValue();
			if (subject != null && subject.length() > SUBJECT_MAX) {
				return EventVerdict.rejected(rawId, "subject.tooLong");
			}
		}

		JsonNode occurredAtNode = node.path("occurredAt");
		if (isAbsent(occurredAtNode)) {
			return EventVerdict.rejected(rawId, "occurredAt.missing");
		}
		// Largata's clock is trusted within the window: a little future skew is fine.
		Instant occurredAt = parseInstant(occurredAtNode);
		if (occurredAt == null || occurredAt.isBefore(EARLIEST_OCCURRED_AT) || occurredAt.isAfter(latestAllowed)) {
			return EventVerdict.rejected(rawId, "occurredAt.invalid");
		}

		// totals means something only on a Snapshot; anywhere else it is ignored, never a reject.
		String totalsJson = null;
		if (SNAPSHOT.equals(kind)) {
			JsonNode totals = node.path("totals");
			if (!validTotals(totals)) {
				return EventVerdict.rejected(rawId, "totals.invalid");
			}
			totalsJson = objectMapper.writeValueAsString(totals);
		}

		boolean stored = repository.insertIfAbsent(new LargataEvent(id, kind, subject, occurredAt, totalsJson));
		return stored ? EventVerdict.accepted(rawId) : EventVerdict.duplicate(rawId);
	}

	/** An object whose every value is a whole number >= 0; an empty object is a valid Snapshot. */
	private static boolean validTotals(JsonNode totals) {
		if (!totals.isObject()) {
			return false;
		}
		for (Map.Entry<String, JsonNode> counter : totals.properties()) {
			JsonNode value = counter.getValue();
			if (counter.getKey().isBlank() || hasControlCharacter(counter.getKey())
					|| !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Control characters have no business in a kind, a Traveler id or a counter name, and one
	 * of them, NUL, is fatal: Postgres refuses it in text (a 500 for the whole batch, retried
	 * forever) and, inside a Snapshot's JSON keys, makes every later summary fail.
	 */
	private static boolean hasControlCharacter(String value) {
		return value.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
	}

	private static boolean isAbsent(JsonNode node) {
		return node.isMissingNode() || node.isNull();
	}

	private static String stringOrNull(JsonNode node) {
		return node.isString() ? node.stringValue() : null;
	}

	private static UUID parseUuid(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			UUID id = UUID.fromString(raw);
			// UUID.fromString is lenient ("1-1-1-1-1" parses); insist on the canonical form so
			// the id Largata logs is the id worklog stores.
			return id.toString().equalsIgnoreCase(raw) ? id : null;
		} catch (IllegalArgumentException notAUuid) {
			return null;
		}
	}

	private static Instant parseInstant(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(node.stringValue()).toInstant();
		} catch (DateTimeException notAnInstant) {
			return null;
		}
	}
}
