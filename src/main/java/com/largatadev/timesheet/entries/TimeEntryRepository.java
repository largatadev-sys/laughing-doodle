package com.largatadev.timesheet.entries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

	// The CAST()s are load-bearing: without them Postgres's extended protocol can't infer
	// a bind parameter's type when its only occurrence is "? IS NULL" with no other typed
	// context — errors "could not determine data type of parameter $1". Do not simplify
	// back to a bare "(:from IS NULL OR ...)".
	@Query("""
			SELECT e FROM TimeEntry e
			WHERE (CAST(:from AS date) IS NULL OR e.entryDate >= CAST(:from AS date))
			  AND (CAST(:to AS date) IS NULL OR e.entryDate <= CAST(:to AS date))
			  AND (CAST(:userId AS long) IS NULL OR e.userId = CAST(:userId AS long))
			ORDER BY e.entryDate ASC, e.id ASC
			""")
	List<TimeEntry> findByFilters(
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("userId") Long userId);
}
