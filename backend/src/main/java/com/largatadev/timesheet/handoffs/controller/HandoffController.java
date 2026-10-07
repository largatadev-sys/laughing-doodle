package com.largatadev.timesheet.handoffs.controller;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.handoffs.dto.CreateHandoffRequest;
import com.largatadev.timesheet.handoffs.dto.HandoffResponse;
import com.largatadev.timesheet.handoffs.dto.HandoffSummaryResponse;
import com.largatadev.timesheet.handoffs.service.HandoffService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handoffs (Story 28). Standard bearer-JWT surface; every Member reads every Handoff, so
 * there is no ownership check — only that the creator is the caller. No PUT, PATCH or DELETE
 * exists: a Handoff is permanent by the shape of the API.
 */
@RestController
@RequestMapping("/api/handoffs")
public class HandoffController {

	/**
	 * The most a create body may be, read before anything else judges it: a 1,000,000-character
	 * text (the service's rule) is at most ~4 MB of UTF-8, or ~6 MB once JSON-escaped, plus the
	 * ids — 8 MB leaves headroom without ever buffering an unbounded body.
	 */
	static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

	private final HandoffService handoffService;
	private final ObjectMapper objectMapper;

	public HandoffController(HandoffService handoffService, ObjectMapper objectMapper) {
		this.handoffService = handoffService;
		this.objectMapper = objectMapper;
	}

	@PostMapping
	public ResponseEntity<HandoffResponse> create(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			InputStream body) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(handoffService.create(read(body), authenticatedUser.userId()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<HandoffResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(handoffService.get(id));
	}

	@GetMapping
	public ResponseEntity<List<HandoffSummaryResponse>> list() {
		return ResponseEntity.ok(handoffService.list());
	}

	/**
	 * The body is read by hand rather than bound to a type, so that malformed JSON or an id
	 * that isn't a UUID comes back as this API's own 400 envelope instead of falling through
	 * to the catch-all 500. Only the JSON shape is judged here; the rules live in the service.
	 */
	private CreateHandoffRequest read(InputStream body) {
		byte[] bytes = bounded(body);
		JsonNode root;
		try {
			root = bytes.length == 0 ? null : objectMapper.readTree(bytes);
		} catch (JacksonException malformed) {
			throw invalid("body", "must be valid JSON");
		}
		if (root == null || !root.isObject()) {
			throw invalid("body", "must be a JSON object");
		}
		return new CreateHandoffRequest(reportIds(root.get("reportIds")), text(root.get("text")));
	}

	/** At most one byte past the limit is ever read, so an oversized body is never held whole. */
	private static byte[] bounded(InputStream body) {
		byte[] bytes;
		try {
			bytes = body.readNBytes(MAX_BODY_BYTES + 1);
		} catch (IOException unreadable) {
			throw new UncheckedIOException(unreadable);
		}
		if (bytes.length > MAX_BODY_BYTES) {
			throw invalid("body", "must be at most " + MAX_BODY_BYTES / (1024 * 1024) + " MB");
		}
		return bytes;
	}

	private static List<UUID> reportIds(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isArray()) {
			throw invalid("reportIds", "must be a list of Report ids");
		}
		List<UUID> ids = new ArrayList<>(node.size());
		for (JsonNode element : node) {
			if (!element.isString()) {
				throw invalid("reportIds", "must be a list of Report ids");
			}
			try {
				ids.add(UUID.fromString(element.asString()));
			} catch (IllegalArgumentException notAUuid) {
				throw invalid("reportIds", "must be a list of Report ids");
			}
		}
		return ids;
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isString()) {
			throw invalid("text", "must be a string");
		}
		return node.asString();
	}

	private static ValidationException invalid(String field, String problem) {
		return new ValidationException("Invalid handoff", Map.of(field, problem));
	}
}
