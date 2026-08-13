package com.largatadev.timesheet.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportScreenshotRepository extends JpaRepository<ReportScreenshot, ReportScreenshot.Key> {

	/** Which report has which screenshot slots — no bytes. */
	record Slot(UUID reportId, int ordinal) {
	}

	/** Ordinals only — the list endpoint must never pull image bytes into memory to render a
	 * count (the whole reason the response carries ordinals rather than data). */
	@Query("""
			SELECT new com.largatadev.timesheet.reports.ReportScreenshotRepository$Slot(
			           s.key.reportId, s.key.ordinal)
			FROM ReportScreenshot s
			WHERE s.key.reportId IN :reportIds
			ORDER BY s.key.ordinal ASC
			""")
	List<Slot> findOrdinalsByReportIds(@Param("reportIds") List<UUID> reportIds);

	default Optional<ReportScreenshot> findOne(UUID reportId, int ordinal) {
		return findById(new ReportScreenshot.Key(reportId, (short) ordinal));
	}
}
