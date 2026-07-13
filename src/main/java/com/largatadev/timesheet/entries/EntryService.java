package com.largatadev.timesheet.entries;

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
		validate(request);

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

	private void validate(CreateEntryRequest request) {
		Map<String, Object> details = new HashMap<>();

		if (request.entryDate() == null) {
			details.put("entryDate", "is required");
		}
		if (request.durationMin() == null || request.durationMin() <= 0) {
			details.put("durationMin", "must be greater than 0");
		}
		if (request.description() == null || request.description().isBlank()) {
			details.put("description", "is required");
		}

		if (!details.isEmpty()) {
			throw new ValidationException("Invalid entry", details);
		}
	}
}
