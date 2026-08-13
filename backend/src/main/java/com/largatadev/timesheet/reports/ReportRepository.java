package com.largatadev.timesheet.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	// COALESCE rather than "(:status IS NULL OR ...)" for the same reason as
	// TimeEntryRepository.findByFilters — see the note there.
	// Ordered by submittedAt (the reporter's action), not receivedAt: a retried delivery must
	// not reorder the inbox. Ties break on id so the order is total and stable.
	@Query("""
			SELECT r FROM Report r
			WHERE r.status = COALESCE(:status, r.status)
			ORDER BY r.submittedAt DESC, r.id DESC
			""")
	List<Report> findFiltered(@Param("status") ReportStatus status);
}
