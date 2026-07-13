package com.largatadev.timesheet.entries;

import com.largatadev.timesheet.error.ValidationException;
import com.largatadev.timesheet.users.Role;
import com.largatadev.timesheet.users.User;
import com.largatadev.timesheet.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryServiceTest {

	@Mock
	TimeEntryRepository timeEntryRepository;

	@Mock
	UserRepository userRepository;

	@Mock
	User user;

	EntryService entryService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		entryService = new EntryService(timeEntryRepository, userRepository);
	}

	@Test
	void validRequestPersistsEntryStampedWithCallersUserIdAndReturnsAuthorName() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(user.getName()).thenReturn("Member One");
		when(timeEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var request = new CreateEntryRequest(LocalDate.of(2026, 7, 10), 90, "Wrote the Story 3 plan");

		EntryResponse response = entryService.create(1L, request);

		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.authorName()).isEqualTo("Member One");
		assertThat(response.entryDate()).isEqualTo(LocalDate.of(2026, 7, 10));
		assertThat(response.durationMin()).isEqualTo(90);
		assertThat(response.description()).isEqualTo("Wrote the Story 3 plan");
		assertThat(response.createdAt()).isEqualTo(response.updatedAt());
	}

	@Test
	void nonPositiveDurationThrowsValidationExceptionAndNeverPersists() {
		var request = new CreateEntryRequest(LocalDate.of(2026, 7, 10), 0, "Some work");

		assertThatThrownBy(() -> entryService.create(1L, request))
				.isInstanceOf(ValidationException.class);

		verify(timeEntryRepository, never()).save(any());
	}

	@Test
	void missingEntryDateThrowsValidationExceptionAndNeverPersists() {
		var request = new CreateEntryRequest(null, 30, "Some work");

		assertThatThrownBy(() -> entryService.create(1L, request))
				.isInstanceOf(ValidationException.class);

		verify(timeEntryRepository, never()).save(any());
	}

	@Test
	void missingDescriptionThrowsValidationExceptionAndNeverPersists() {
		var request = new CreateEntryRequest(LocalDate.of(2026, 7, 10), 30, null);

		assertThatThrownBy(() -> entryService.create(1L, request))
				.isInstanceOf(ValidationException.class);

		verify(timeEntryRepository, never()).save(any());
	}

	@Test
	void blankDescriptionThrowsValidationExceptionAndNeverPersists() {
		var request = new CreateEntryRequest(LocalDate.of(2026, 7, 10), 30, "   ");

		assertThatThrownBy(() -> entryService.create(1L, request))
				.isInstanceOf(ValidationException.class);

		verify(timeEntryRepository, never()).save(any());
	}
}
