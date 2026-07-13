package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.error.ForbiddenException;
import com.largatadev.timesheet.error.NotFoundException;
import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EntryService {

	private final TimeEntryRepository timeEntryRepository;
	private final UserRepository userRepository;

	public EntryService(TimeEntryRepository timeEntryRepository, UserRepository userRepository) {
		this.timeEntryRepository = timeEntryRepository;
		this.userRepository = userRepository;
	}

	public EntryResponse create(Long userId, CreateEntryRequest request) {
		validate(request.entryDate(), request.durationMin(), request.description());

		OffsetDateTime now = OffsetDateTime.now();
		TimeEntry entry = new TimeEntry(
				userId,
				request.entryDate(),
				request.durationMin(),
				request.description(),
				now,
				now);

		TimeEntry saved = timeEntryRepository.save(entry);

		User author = userRepository.findById(userId).orElseThrow();
		return EntryResponse.of(saved, author.getName());
	}

	public EntryResponse update(Long id, Long callerId, UpdateEntryRequest request) {
		TimeEntry entry = timeEntryRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Entry not found"));

		if (!entry.getUserId().equals(callerId)) {
			throw new ForbiddenException("Only the author may edit this entry");
		}

		validate(request.entryDate(), request.durationMin(), request.description());

		entry.update(request.entryDate(), request.durationMin(), request.description(), OffsetDateTime.now());
		TimeEntry saved = timeEntryRepository.save(entry);

		User author = userRepository.findById(callerId).orElseThrow();
		return EntryResponse.of(saved, author.getName());
	}

	public List<EntryResponse> list(LocalDate from, LocalDate to, Long userId) {
		List<TimeEntry> entries = timeEntryRepository.findByFilters(from, to, userId);

		Map<Long, String> namesByUserId = userRepository
				.findAllById(entries.stream().map(TimeEntry::getUserId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(User::getId, User::getName));

		return entries.stream()
				.map(entry -> EntryResponse.of(entry, namesByUserId.get(entry.getUserId())))
				.toList();
	}

	private void validate(LocalDate entryDate, Integer durationMin, String description) {
		Map<String, Object> details = new HashMap<>();

		if (entryDate == null) {
			details.put("entryDate", "is required");
		}
		if (durationMin == null || durationMin <= 0) {
			details.put("durationMin", "must be greater than 0");
		}
		if (description == null || description.isBlank()) {
			details.put("description", "is required");
		}

		if (!details.isEmpty()) {
			throw new ValidationException("Invalid entry", details);
		}
	}
}
