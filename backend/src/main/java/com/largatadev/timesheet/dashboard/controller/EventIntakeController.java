package com.largatadev.timesheet.dashboard.controller;

import com.largatadev.timesheet.dashboard.dto.EventBatchResult;
import com.largatadev.timesheet.dashboard.service.EventIntakeService;

import com.largatadev.timesheet.error.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Events intake (ADR-014), the second route behind the relay's shared secret. It joins
 * the intake filter chain by path alone ({@code /api/intake/**}), so the secret is checked in
 * {@code IntakeSecretFilter} before this controller is reached, a Member's JWT cannot open it,
 * and the secret cannot open anything else.
 *
 * <p>Only the envelope can fail the whole call with a {@code 400}: an unparseable body, or
 * {@code events} missing, empty, or longer than {@value #MAX_BATCH}. Everything about an
 * individual Event is a per-Event verdict in a {@code 200}.
 */
@RestController
@RequestMapping("/api/intake/events")
public class EventIntakeController {

	static final int MAX_BATCH = 500;

	private final EventIntakeService eventIntakeService;
	private final ObjectMapper objectMapper;

	public EventIntakeController(EventIntakeService eventIntakeService, ObjectMapper objectMapper) {
		this.eventIntakeService = eventIntakeService;
		this.objectMapper = objectMapper;
	}

	/**
	 * The body is read by hand rather than bound to a type so that a malformed batch from the
	 * other repo comes back as this API's own {@code 400} envelope instead of a framework
	 * deserialization error, and so that one ill-typed Event becomes a verdict, not a failure.
	 */
	@PostMapping
	public ResponseEntity<EventBatchResult> intake(@RequestBody(required = false) byte[] body) {
		return ResponseEntity.ok(eventIntakeService.accept(readEvents(body)));
	}

	private List<JsonNode> readEvents(byte[] body) {
		JsonNode root;
		try {
			root = body == null || body.length == 0 ? null : objectMapper.readTree(body);
		} catch (JacksonException malformed) {
			// Never echo the body back: it is unvalidated foreign input.
			throw invalid("body", "must be valid JSON");
		}
		if (root == null || !root.isObject()) {
			throw invalid("body", "must be a JSON object");
		}

		JsonNode events = root.path("events");
		if (!events.isArray()) {
			throw invalid("events", "is required");
		}
		if (events.isEmpty()) {
			throw invalid("events", "must not be empty");
		}
		if (events.size() > MAX_BATCH) {
			throw invalid("events", "must be at most " + MAX_BATCH);
		}

		List<JsonNode> list = new ArrayList<>(events.size());
		events.forEach(list::add);
		return list;
	}

	private static ValidationException invalid(String field, String problem) {
		return new ValidationException("Invalid events batch", Map.of(field, problem));
	}
}
