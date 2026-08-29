package com.largatadev.timesheet.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportNoteRepository extends JpaRepository<ReportNote, UUID> {

	/** Every note for the reports currently on screen, in one query — oldest-first, ties broken
	 *  on id so two notes written in the same millisecond still have a total, stable order. */
	List<ReportNote> findByReportIdInOrderByCreatedAtAscIdAsc(List<UUID> reportIds);

	List<ReportNote> findByReportIdOrderByCreatedAtAscIdAsc(UUID reportId);

	/** Both ids, together: a note id that belongs to another report is a 404 here, not a way to
	 *  reach a note through a report you happened to name. */
	Optional<ReportNote> findByIdAndReportId(UUID id, UUID reportId);
}
