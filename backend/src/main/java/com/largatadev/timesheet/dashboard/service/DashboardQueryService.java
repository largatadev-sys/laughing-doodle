package com.largatadev.timesheet.dashboard.service;

import com.largatadev.timesheet.dashboard.dto.ActiveSeries;
import com.largatadev.timesheet.dashboard.dto.DashboardSeries;
import com.largatadev.timesheet.dashboard.dto.DashboardSummary;
import com.largatadev.timesheet.dashboard.repository.LargataEventRepository;
import com.largatadev.timesheet.dashboard.domain.SeriesBucket;

import com.largatadev.timesheet.dashboard.dto.DashboardSummary.CounterTotal;
import com.largatadev.timesheet.dashboard.repository.LargataEventRepository.CounterRow;
import com.largatadev.timesheet.dashboard.repository.LargataEventRepository.Freshness;
import com.largatadev.timesheet.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The aggregation rules (spec, "Aggregation rules"), computed on read from the one log. No
 * caching and no materialised views: the volume is trivial, and ADR-014 names the point at
 * which that stops being true.
 *
 * <p>Day boundaries are computed here, in Java, from the viewer's zone, and handed to SQL as
 * instants — the database never interprets a zone name, so Java's tz rules are the only ones
 * in play and "today" cannot disagree with itself between the two.
 */
@Service
public class DashboardQueryService {

	static final String CREATED = ".created";
	static final String DELETED = ".deleted";

	/** The {@code baseline} a total reports when no Snapshot has ever carried its counter. */
	static final String NO_BASELINE = "none";

	/** Snapshots are expected hourly; two missed in a row is silence, not jitter. */
	static final Duration SILENT_AFTER = Duration.ofHours(2);

	private final LargataEventRepository repository;
	private final Clock clock;

	DashboardQueryService(LargataEventRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Every figure in the answer comes from the same state of the log. Postgres's default READ
	 * COMMITTED gives each statement its own snapshot, so freshness, active today and the
	 * totals could otherwise straddle an hourly Snapshot's commit; REPEATABLE READ holds one
	 * snapshot for the whole transaction, and a read-only transaction there never fails with a
	 * serialization error.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DashboardSummary summary(ZoneId zone) {
		Instant now = clock.instant();

		Freshness freshness = repository.freshness();
		Instant lastSnapshotAt = freshness.lastSnapshotAt();
		boolean silent = lastSnapshotAt == null || lastSnapshotAt.isBefore(now.minus(SILENT_AFTER));

		LocalDate today = LocalDate.ofInstant(now, zone);
		long activeToday = repository.distinctSubjectsBetween(
				today.atStartOfDay(zone).toInstant(),
				today.plusDays(1).atStartOfDay(zone).toInstant());

		return new DashboardSummary(
				now,
				zone.getId(),
				freshness.lastEventAt(),
				lastSnapshotAt,
				silent,
				silent ? lastSnapshotAt : null,
				activeToday,
				totals());
	}

	/** The most points one series answers — about three years of days. A guard, not a feature. */
	static final int MAX_POINTS = 1100;

	/**
	 * Created and deleted per bucket of the viewer's calendar, empty buckets as zeros. Every
	 * {@code .created} counts here whatever its date — including ones before the latest
	 * Snapshot, which the totals ignore: late delivery is still history.
	 *
	 * @param from first day of the range in the zone, or null for the bucket's default
	 * @param to last day of the range in the zone, or null for today
	 */
	@Transactional(readOnly = true)
	public DashboardSeries series(String counter, SeriesBucket bucket, ZoneId zone, LocalDate from, LocalDate to) {
		String createdKind = counter + CREATED;
		String deletedKind = counter + DELETED;
		Instant first = repository.firstOccurrence(createdKind, deletedKind);
		if (first == null) {
			return new DashboardSeries(counter, bucket.wireName(), zone.getId(), List.of());
		}

		Range range = range(bucket, zone, from, to, first);
		long[][] counts = repository.countsPerBucket(createdKind, deletedKind, range.bounds());
		List<DashboardSeries.Point> points = new ArrayList<>(range.starts().size());
		for (int i = 0; i < range.starts().size(); i++) {
			points.add(new DashboardSeries.Point(bucket.label(range.starts().get(i)), counts[i][0], counts[i][1]));
		}
		return new DashboardSeries(counter, bucket.wireName(), zone.getId(), points);
	}

	/**
	 * Travelers active per bucket — "active today" for every bucket in the range: distinct
	 * subjects across every kind, in the viewer's calendar. So today's day bucket always equals
	 * the summary's {@code activeToday}. Empty points when no Event has ever named a Traveler.
	 */
	@Transactional(readOnly = true)
	public ActiveSeries active(SeriesBucket bucket, ZoneId zone, LocalDate from, LocalDate to) {
		Instant first = repository.firstNamedOccurrence();
		if (first == null) {
			return new ActiveSeries(bucket.wireName(), zone.getId(), List.of());
		}

		Range range = range(bucket, zone, from, to, first);
		long[] counts = repository.distinctSubjectsPerBucket(range.bounds());
		List<ActiveSeries.Point> points = new ArrayList<>(range.starts().size());
		for (int i = 0; i < range.starts().size(); i++) {
			points.add(new ActiveSeries.Point(bucket.label(range.starts().get(i)), counts[i]));
		}
		return new ActiveSeries(bucket.wireName(), zone.getId(), points);
	}

	/** The buckets a period read covers: each bucket's first day, and the instants bounding them
	 *  (one more than the buckets — the last is where the range ends). */
	private record Range(List<LocalDate> starts, List<Instant> bounds) {
	}

	/**
	 * The range for a period read. {@code to} defaults to today in the zone; {@code from} to
	 * the bucket's default length back from it, or — for years — the day of {@code first}, the
	 * earliest relevant Event. Bounds are computed here, in Java, so DST-length days and the
	 * zone's rules never reach SQL.
	 */
	private Range range(SeriesBucket bucket, ZoneId zone, LocalDate from, LocalDate to, Instant first) {
		LocalDate end = to != null ? to : LocalDate.ofInstant(clock.instant(), zone);
		LocalDate start = from != null ? from : bucket.defaultFrom(end);
		if (start == null) {
			LocalDate firstDay = LocalDate.ofInstant(first, zone);
			start = firstDay.isBefore(end) ? firstDay : end;
		}
		if (start.isAfter(end)) {
			throw new ValidationException("Invalid query parameter", Map.of("from", "must not be after to"));
		}

		List<LocalDate> starts = new ArrayList<>();
		for (LocalDate b = bucket.floor(start); !b.isAfter(end); b = bucket.next(b)) {
			starts.add(b);
			if (starts.size() > MAX_POINTS) {
				throw new ValidationException("Invalid query parameter",
						Map.of("from", "range must span at most " + MAX_POINTS + " " + bucket.wireName() + "s"));
			}
		}

		List<Instant> bounds = new ArrayList<>(starts.size() + 1);
		for (LocalDate b : starts) {
			bounds.add(b.atStartOfDay(zone).toInstant());
		}
		bounds.add(bucket.next(starts.getLast()).atStartOfDay(zone).toInstant());
		return new Range(starts, bounds);
	}

	/**
	 * Total = the counter's baseline Snapshot value + created − deleted after it; with no
	 * baseline, created − deleted over all time, flagged {@code "none"}. Ordered as the latest
	 * Snapshot lists its counters, then every other counter alphabetically.
	 */
	private List<CounterTotal> totals() {
		List<CounterRow> rows = new ArrayList<>(repository.counterTotals());
		rows.sort(Comparator
				.comparing(CounterRow::position, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(CounterRow::counter));

		List<CounterTotal> totals = new ArrayList<>(rows.size());
		for (CounterRow row : rows) {
			totals.add(row.baselineAt() == null
					? new CounterTotal(row.counter(), row.delta(), NO_BASELINE)
					: new CounterTotal(row.counter(), row.baselineValue() + row.delta(), row.baselineAt().toString()));
		}
		return totals;
	}
}
