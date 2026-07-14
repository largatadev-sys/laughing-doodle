package com.largatadev.timesheet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SchemaMigrationTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@BeforeAll
	static void migrate() {
		org.flywaydb.core.Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.load()
				.migrate();
	}

	private Connection connect() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	@Test
	void migrationCreatesUsersAndTimeEntriesTables() throws SQLException {
		try (Connection conn = connect()) {
			try (var rs = conn.getMetaData().getTables(null, null, "users", null)) {
				assertThat(rs.next()).as("users table exists").isTrue();
			}
			try (var rs = conn.getMetaData().getTables(null, null, "time_entries", null)) {
				assertThat(rs.next()).as("time_entries table exists").isTrue();
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void rejectsNonPositiveDurationMin(int durationMin) throws SQLException {
		try (Connection conn = connect()) {
			insertUser(conn, "durationtest" + durationMin);

			assertThatThrownBy(() -> {
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO time_entries (user_id, entry_date, duration_min, description) " +
								"VALUES ((SELECT id FROM users WHERE username = ?), CURRENT_DATE, ?, 'test')")) {
					ps.setString(1, "durationtest" + durationMin);
					ps.setInt(2, durationMin);
					ps.executeUpdate();
				}
			}).isInstanceOf(SQLException.class);
		}
	}

	@Test
	void rejectsDuplicateUsernameDifferingOnlyByCase() throws SQLException {
		try (Connection conn = connect()) {
			insertUser(conn, "jsmith");

			assertThatThrownBy(() -> insertUser(conn, "JSmith"))
					.isInstanceOf(SQLException.class);
		}
	}

	private void insertUser(Connection conn, String username) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO users (name, username, password_hash, role) VALUES (?, ?, 'hash', 'member')")) {
			ps.setString(1, "Test User");
			ps.setString(2, username);
			ps.executeUpdate();
		}
	}
}
