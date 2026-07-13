package com.largatadev.timesheet.entries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

	// Must stay COALESCE(:param, e.column). A bare "(:param IS NULL OR col op :param)" leaves
	// Postgres unable to infer that parameter's type when it's null. A CAST-guarded version
	// of the same check only fixes that for a query's first, unprepared execution — once the
	// driver reuses a server-side prepared statement, a null parameter gets bound as `bytea`
	// by default and the CAST is then rejected. COALESCE never compares the bare parameter to
	// NULL, so neither failure mode applies.
	@Query("""
			SELECT e FROM TimeEntry e
			WHERE e.entryDate >= COALESCE(:from, e.entryDate)
			  AND e.entryDate <= COALESCE(:to, e.entryDate)
			  AND e.userId = COALESCE(:userId, e.userId)
			ORDER BY e.entryDate ASC, e.id ASC
			""")
	List<TimeEntry> findByFilters(
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("userId") Long userId);
}
