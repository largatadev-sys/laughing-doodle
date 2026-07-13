package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
