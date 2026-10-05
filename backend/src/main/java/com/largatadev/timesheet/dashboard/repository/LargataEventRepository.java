package com.largatadev.timesheet.dashboard.repository;

import com.largatadev.timesheet.dashboard.domain.LargataEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Persistence for the append-only Events log. Plain JDBC rather than JPA: the log is written
 * once and only ever counted, so there is no entity lifecycle to manage — and the one write
 * needs {@code ON CONFLICT DO NOTHING}, which is what makes a replay a no-op. There is
 * deliberately no update and no delete here.
 */
@Repository
public class LargataEventRepository {

	private final JdbcTemplate jdbc;

	LargataEventRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Stores the Event unless its id is already in the log. Returns false for a replay. */
	public boolean insertIfAbsent(LargataEvent event) {
		int inserted = jdbc.update("""
				INSERT INTO largata_events (id, kind, subject, occurred_at, totals)
				VALUES (?, ?, ?, ?, CAST(? AS json))
				ON CONFLICT (id) DO NOTHING
				""",
				event.id(),
				event.kind(),
				event.subject(),
				utc(event.occurredAt()),
				event.totalsJson());
		return inserted == 1;
	}

	// ---- reads: every Dashboard figure is counted from the log on request --------------------

	public record Freshness(Instant lastEventAt, Instant lastSnapshotAt) {
	}

	/**
	 * One counter's raw figures: its baseline (the value in the latest Snapshot that carries it,
	 * and that Snapshot's time — both null when no Snapshot ever has), the signed sum of its
	 * {@code .created} / {@code .deleted} Events after that baseline, and its position in the
	 * latest Snapshot (null when the latest Snapshot does not list it).
	 */
	public record CounterRow(String counter, Long baselineValue, Instant baselineAt, long delta, Long position) {
	}

	public Freshness freshness() {
		return jdbc.queryForObject("""
				SELECT max(occurred_at) AS last_event,
				       max(occurred_at) FILTER (WHERE kind = 'snapshot') AS last_snapshot
				FROM largata_events
				""",
				(rs, row) -> new Freshness(instant(rs, "last_event"), instant(rs, "last_snapshot")));
	}

	/** Distinct Travelers named by any Event in [from, to). */
	public long distinctSubjectsBetween(Instant from, Instant to) {
		Long count = jdbc.queryForObject("""
				SELECT count(DISTINCT subject) FROM largata_events
				WHERE subject IS NOT NULL AND occurred_at >= ? AND occurred_at < ?
				""",
				Long.class, utc(from), utc(to));
		return count == null ? 0 : count;
	}

	/** Both interpreted suffixes, {@code .created} and {@code .deleted}, are this long. */
	private static final int SUFFIX_LENGTH = ".created".length();

	/**
	 * Every counter's figures in ONE statement, so the baseline each total starts from and the
	 * changes counted after it come from the same snapshot of the log — an hourly Snapshot
	 * landing between two separate queries could otherwise drop that hour's changes from a
	 * response. It also walks the Snapshots' totals once per request rather than twice.
	 *
	 * <p>{@code baseline}: per counter, the latest Snapshot that carries it — not merely the
	 * latest Snapshot, so a counter Largata stops reporting keeps the baseline it last had.
	 * {@code changes}: each {@code .created} / {@code .deleted} dated strictly after its
	 * counter's baseline (an Event at or before it is already inside the Snapshot's number),
	 * or over all time when the counter has none. {@code latest}: the latest Snapshot's keys in
	 * the order Largata listed them — the JSON column keeps that order.
	 */
	public List<CounterRow> counterTotals() {
		return jdbc.query("""
				WITH baseline AS (
				    SELECT DISTINCT ON (t.key) t.key AS counter, t.value::bigint AS value, e.occurred_at AS at
				    FROM largata_events e
				    CROSS JOIN LATERAL json_each_text(e.totals) AS t(key, value)
				    WHERE e.kind = 'snapshot'
				    ORDER BY t.key, e.occurred_at DESC, e.received_at DESC, e.id DESC
				),
				changes AS (
				    SELECT left(e.kind, length(e.kind) - ?) AS counter,
				           sum(CASE WHEN e.kind LIKE '%.created' THEN 1 ELSE -1 END) AS delta
				    FROM largata_events e
				    LEFT JOIN baseline b ON b.counter = left(e.kind, length(e.kind) - ?)
				    WHERE (e.kind LIKE '_%.created' OR e.kind LIKE '_%.deleted')
				      AND (b.at IS NULL OR e.occurred_at > b.at)
				    GROUP BY 1
				),
				latest AS (
				    SELECT t.key AS counter, t.ord AS position
				    FROM (SELECT totals FROM largata_events
				          WHERE kind = 'snapshot'
				          ORDER BY occurred_at DESC, received_at DESC, id DESC
				          LIMIT 1) s
				    CROSS JOIN LATERAL json_each(s.totals) WITH ORDINALITY AS t(key, value, ord)
				)
				SELECT coalesce(b.counter, c.counter) AS counter, b.value AS baseline_value,
				       b.at AS baseline_at, coalesce(c.delta, 0) AS delta, l.position
				FROM baseline b
				FULL JOIN changes c ON c.counter = b.counter
				LEFT JOIN latest l ON l.counter = coalesce(b.counter, c.counter)
				""",
				(rs, row) -> new CounterRow(
						rs.getString("counter"),
						rs.getObject("baseline_value", Long.class),
						instant(rs, "baseline_at"),
						rs.getLong("delta"),
						rs.getObject("position", Long.class)),
				SUFFIX_LENGTH, SUFFIX_LENGTH);
	}

	/** The earliest occurredAt among these kinds, or null when none has ever arrived. */
	public Instant firstOccurrence(String createdKind, String deletedKind) {
		return jdbc.queryForObject(
				"SELECT min(occurred_at) AS first FROM largata_events WHERE kind IN (?, ?)",
				(rs, row) -> instant(rs, "first"),
				createdKind, deletedKind);
	}

	/**
	 * Created and deleted counts per bucket, where bucket {@code i} (0-based) is
	 * [{@code bounds[i]}, {@code bounds[i+1]}). The bounds are the viewer's bucket starts,
	 * computed in Java; {@code width_bucket} only sorts instants into them, so the database
	 * never needs to know a zone name (and DST-length days come out right for free).
	 * Returns {@code [created, deleted]} per bucket, zeros included.
	 */
	public long[][] countsPerBucket(String createdKind, String deletedKind, List<Instant> bounds) {
		long[][] counts = new long[bounds.size() - 1][2];
		jdbc.query("""
				SELECT width_bucket(occurred_at, CAST(? AS timestamptz[])) AS b, kind, count(*) AS n
				FROM largata_events
				WHERE kind IN (?, ?) AND occurred_at >= ? AND occurred_at < ?
				GROUP BY b, kind
				""",
				rs -> {
					int bucket = rs.getInt("b") - 1;
					int column = createdKind.equals(rs.getString("kind")) ? 0 : 1;
					counts[bucket][column] = rs.getLong("n");
				},
				thresholds(bounds), createdKind, deletedKind,
				utc(bounds.getFirst()), utc(bounds.getLast()));
		return counts;
	}

	/** The earliest occurredAt of any Event that names a Traveler, or null when none has. */
	public Instant firstNamedOccurrence() {
		return jdbc.queryForObject(
				"SELECT min(occurred_at) AS first FROM largata_events WHERE subject IS NOT NULL",
				(rs, row) -> instant(rs, "first"));
	}

	/** Distinct Travelers per bucket, bucket {@code i} being [{@code bounds[i]}, {@code bounds[i+1]}). */
	public long[] distinctSubjectsPerBucket(List<Instant> bounds) {
		long[] counts = new long[bounds.size() - 1];
		jdbc.query("""
				SELECT width_bucket(occurred_at, CAST(? AS timestamptz[])) AS b, count(DISTINCT subject) AS n
				FROM largata_events
				WHERE subject IS NOT NULL AND occurred_at >= ? AND occurred_at < ?
				GROUP BY b
				""",
				rs -> {
					counts[rs.getInt("b") - 1] = rs.getLong("n");
				},
				thresholds(bounds), utc(bounds.getFirst()), utc(bounds.getLast()));
		return counts;
	}

	/** A Postgres array literal of the bounds — ISO instants we generated, so nothing to escape. */
	private static String thresholds(List<Instant> bounds) {
		StringBuilder literal = new StringBuilder("{");
		for (int i = 0; i < bounds.size(); i++) {
			literal.append(i == 0 ? "" : ",").append('"').append(bounds.get(i)).append('"');
		}
		return literal.append('}').toString();
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
