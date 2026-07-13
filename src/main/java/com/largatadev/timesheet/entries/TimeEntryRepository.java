package com.largatadev.timesheet.entries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

	// COALESCE(:param, e.column) is load-bearing, not stylistic. A bare "(:param IS NULL OR
	// col op :param)" makes Postgres's extended protocol unable to infer the parameter's
	// type ("could not determine data type of parameter $1"); wrapping the null check in an
	// explicit CAST fixes that but only for the first, unprepared execution of the query —
	// once the JDBC driver reuses a server-side prepared statement for a genuinely-null
	// value, it binds that null as `bytea` by default and "cannot cast type bytea to date"
	// resurfaces (only reproduces once a connection re-executes the query — never showed up
	// under a fresh Testcontainers run, only against the app run live). COALESCE against the
	// (never-null) entry column sidesteps both failure modes: when the param is null the
	// comparison degenerates to "col op col" (always true), and Postgres infers the
	// parameter's type from the column it's coalesced with, in every execution path.
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
