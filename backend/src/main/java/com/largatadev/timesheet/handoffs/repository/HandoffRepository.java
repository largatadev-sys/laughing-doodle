package com.largatadev.timesheet.handoffs.repository;

import com.largatadev.timesheet.handoffs.domain.Handoff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence for Handoffs. Plain JDBC, like the dashboard's Events log: a Handoff is written
 * once and only read back, so there is no entity lifecycle — and its ordered child rows are a
 * single batch insert. There is deliberately no update and no delete here.
 */
@Repository
public class HandoffRepository {

	private final JdbcTemplate jdbc;

	HandoffRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A stored Handoff with its creator's display name resolved. */
	public record StoredHandoff(Handoff handoff, String createdByName) {
	}

	public void insert(Handoff handoff) {
		jdbc.update("INSERT INTO handoffs (id, created_by, created_at, text) VALUES (?, ?, ?, ?)",
				handoff.id(), handoff.createdBy(), utc(handoff.createdAt()), handoff.text());

		List<Object[]> rows = new ArrayList<>(handoff.reportIds().size());
		for (int position = 0; position < handoff.reportIds().size(); position++) {
			rows.add(new Object[] {handoff.id(), handoff.reportIds().get(position), position});
		}
		jdbc.batchUpdate("INSERT INTO handoff_reports (handoff_id, report_id, position) VALUES (?, ?, ?)", rows);
	}

	public Optional<StoredHandoff> findById(UUID id) {
		List<UUID> reportIds = reportIdsOf(id);
		List<StoredHandoff> found = jdbc.query("""
				SELECT h.id, h.created_by, h.created_at, h.text, u.name AS created_by_name
				FROM handoffs h
				JOIN users u ON u.id = h.created_by
				WHERE h.id = ?
				""",
				(rs, row) -> new StoredHandoff(
						new Handoff(
								rs.getObject("id", UUID.class),
								rs.getLong("created_by"),
								rs.getObject("created_at", OffsetDateTime.class).toInstant(),
								rs.getString("text"),
								reportIds),
						rs.getString("created_by_name")),
				id);
		return found.stream().findFirst();
	}

	/**
	 * Which of these ids name a Report that exists. Asked of the table directly rather than
	 * through the reports module, so {@code handoffs} never depends on {@code reports} in Java —
	 * the reports read will depend on this module, and the dependency must stay one-way.
	 */
	public Set<UUID> existingReportIds(Collection<UUID> ids) {
		return new HashSet<>(jdbc.query("SELECT id FROM reports WHERE id = ANY(?)",
				(rs, row) -> rs.getObject("id", UUID.class),
				(Object) ids.toArray(new UUID[0])));
	}

	private List<UUID> reportIdsOf(UUID handoffId) {
		return jdbc.query("SELECT report_id FROM handoff_reports WHERE handoff_id = ? ORDER BY position",
				(rs, row) -> rs.getObject("report_id", UUID.class),
				handoffId);
	}

	/** The one Instant → UTC {@code OffsetDateTime} mapping the module uses, for the DB and the API. */
	public static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/** The display name of the user with this id. */
	public String nameOf(long userId) {
		return jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, userId);
	}

	/** One Handoff as the list shows it: never the text, only how many Reports it covers. */
	public record HandoffSummary(UUID id, long createdBy, String createdByName, Instant createdAt, int reportCount) {
	}

	/**
	 * Every Handoff, newest first, in one query. The id breaks a tie on {@code created_at} so the
	 * order is stable between reads.
	 */
	public List<HandoffSummary> findAllSummariesNewestFirst() {
		return jdbc.query("""
				SELECT h.id, h.created_by, u.name AS created_by_name, h.created_at,
				       (SELECT count(*) FROM handoff_reports hr WHERE hr.handoff_id = h.id) AS report_count
				FROM handoffs h
				JOIN users u ON u.id = h.created_by
				ORDER BY h.created_at DESC, h.id DESC
				""",
				(rs, row) -> new HandoffSummary(
						rs.getObject("id", UUID.class),
						rs.getLong("created_by"),
						rs.getString("created_by_name"),
						rs.getObject("created_at", OffsetDateTime.class).toInstant(),
						rs.getInt("report_count")));
	}
}
