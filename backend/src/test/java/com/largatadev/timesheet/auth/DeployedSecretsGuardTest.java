package com.largatadev.timesheet.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A deployed worklog must never run on the repo's visible placeholder secrets: with the JWT
 * placeholder anyone who read the repo can forge a login, and with the intake placeholder
 * anyone can write permanent Events. Locally the placeholders are the point — the gate and
 * bootRun work out of the box.
 */
class DeployedSecretsGuardTest {

	private static final String REAL_JWT = "a-long-random-production-jwt-signing-secret-value-0123456789";
	private static final String REAL_INTAKE = "a-long-random-production-intake-secret-value";

	@Test
	void locallyThePlaceholdersAreFine() {
		assertThatCode(() -> DeployedSecretsGuard.check(env(
				DeployedSecretsGuard.JWT_PLACEHOLDER, DeployedSecretsGuard.INTAKE_PLACEHOLDER, false)))
				.doesNotThrowAnyException();
	}

	@Test
	void deployedWithRealSecretsStarts() {
		assertThatCode(() -> DeployedSecretsGuard.check(env(REAL_JWT, REAL_INTAKE, true)))
				.doesNotThrowAnyException();
	}

	@Test
	void deployedWithEitherPlaceholderRefusesToStartAndNamesTheVariable() {
		assertThatThrownBy(() -> DeployedSecretsGuard.check(env(DeployedSecretsGuard.JWT_PLACEHOLDER, REAL_INTAKE, true)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET")
				.hasMessageNotContaining("REPORTS_INTAKE_SECRET");

		assertThatThrownBy(() -> DeployedSecretsGuard.check(env(REAL_JWT, DeployedSecretsGuard.INTAKE_PLACEHOLDER, true)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("REPORTS_INTAKE_SECRET");
	}

	@Test
	void deployedWithABlankSecretRefusesToStart() {
		assertThatThrownBy(() -> DeployedSecretsGuard.check(env(REAL_JWT, "  ", true)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("REPORTS_INTAKE_SECRET");
	}

	@Test
	void theErrorNeverContainsASecretValue() {
		assertThatThrownBy(() -> DeployedSecretsGuard.check(env(
				DeployedSecretsGuard.JWT_PLACEHOLDER, DeployedSecretsGuard.INTAKE_PLACEHOLDER, true)))
				.hasMessageNotContaining(DeployedSecretsGuard.JWT_PLACEHOLDER)
				.hasMessageNotContaining(DeployedSecretsGuard.INTAKE_PLACEHOLDER);
	}

	@Test
	void theGuardsPlaceholdersAreTheOnesApplicationPropertiesFallsBackTo() throws IOException {
		// If someone edits a default in application.properties, the guard must follow it.
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			String properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(properties)
					.contains("${JWT_SECRET:" + DeployedSecretsGuard.JWT_PLACEHOLDER + "}")
					.contains("${REPORTS_INTAKE_SECRET:" + DeployedSecretsGuard.INTAKE_PLACEHOLDER + "}");
		}
	}

	private static MockEnvironment env(String jwtSecret, String intakeSecret, boolean onRailway) {
		MockEnvironment env = new MockEnvironment()
				.withProperty("jwt.secret", jwtSecret)
				.withProperty("reports.intake-secret", intakeSecret);
		if (onRailway) {
			env.withProperty("RAILWAY_ENVIRONMENT_NAME", "production");
		}
		return env;
	}
}
