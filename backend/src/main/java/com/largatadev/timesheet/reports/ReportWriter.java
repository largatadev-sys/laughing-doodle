package com.largatadev.timesheet.reports;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The one transactional write in intake: a Report and its screenshots land together or not at
 * all. It lives in its own bean rather than as a method on {@link ReportService} because the
 * service must call it <em>through</em> the transaction proxy — a self-invocation would run
 * outside any transaction, and a half-written report is exactly what the all-or-nothing rule
 * forbids. The service stays non-transactional so it can still read after a lost key race.
 */
@Component
class ReportWriter {

	private final ReportRepository reportRepository;
	private final ReportScreenshotRepository screenshotRepository;

	ReportWriter(ReportRepository reportRepository, ReportScreenshotRepository screenshotRepository) {
		this.reportRepository = reportRepository;
		this.screenshotRepository = screenshotRepository;
	}

	@Transactional
	Report insert(Report report, List<IncomingScreenshot> screenshots) {
		Report saved = reportRepository.saveAndFlush(report);

		for (int ordinal = 0; ordinal < screenshots.size(); ordinal++) {
			IncomingScreenshot screenshot = screenshots.get(ordinal);
			screenshotRepository.save(new ReportScreenshot(
					saved.getId(), ordinal, screenshot.contentType(), screenshot.bytes()));
		}
		screenshotRepository.flush();

		return saved;
	}
}
