package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entries")
public class EntryController {

	private final EntryService entryService;

	public EntryController(EntryService entryService) {
		this.entryService = entryService;
	}

	@PostMapping
	public ResponseEntity<EntryResponse> create(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestBody CreateEntryRequest request) {
		EntryResponse response = entryService.create(authenticatedUser.userId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{id}")
	public ResponseEntity<EntryResponse> update(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long id,
			@RequestBody UpdateEntryRequest request) {
		EntryResponse response = entryService.update(id, authenticatedUser.userId(), request);
		return ResponseEntity.ok(response);
	}

	@GetMapping
	public ResponseEntity<List<EntryResponse>> list(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Long userId) {
		return ResponseEntity.ok(entryService.list(from, to, userId));
	}
}
