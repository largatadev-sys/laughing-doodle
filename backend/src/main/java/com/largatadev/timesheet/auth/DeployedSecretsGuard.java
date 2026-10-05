package com.largatadev.timesheet.auth;

import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a <em>deployed</em> worklog on the repo's visible placeholder secrets.
 *
 * <p>Both secrets fall back to a committed placeholder so local dev, the gate and the test
 * suite work out of the box (application.properties). On Railway that fallback would be a
 * silent hole: a missing or renamed variable on a new environment and the app runs with a
 * secret anyone who has read the repo knows — a forgeable JWT for every Member, and an intake
 * door into the append-only Events log, which has no delete. So when the app is running on
 * Railway (it sets RAILWAY_* variables in every deployment), a placeholder or blank secret is
 * a startup failure that names the variable, never its value.
 *
 * <p>Constructed at startup as a bean, so a failure stops the context before any request is
 * served.
 */
@Component
public class DeployedSecretsGuard {

	static final String JWT_PLACEHOLDER = "dev-only-placeholder-secret-not-for-prod-use-change-me";
	static final String INTAKE_PLACEHOLDER = "dev-only-placeholder-intake-secret-change-me";

	/** Any of these means "running on Railway" — the platform sets them in every deployment. */
	static final List<String> PLATFORM_MARKERS =
			List.of("RAILWAY_ENVIRONMENT_NAME", "RAILWAY_ENVIRONMENT", "RAILWAY_PROJECT_ID");

	public DeployedSecretsGuard(Environment environment) {
		check(environment);
	}

	static void check(PropertyResolver env) {
		if (!deployed(env)) {
			return;
		}
		List<String> problems = new ArrayList<>();
		if (unusable(env.getProperty("jwt.secret"), JWT_PLACEHOLDER)) {
			problems.add("JWT_SECRET");
		}
		if (unusable(env.getProperty("reports.intake-secret"), INTAKE_PLACEHOLDER)) {
			problems.add("REPORTS_INTAKE_SECRET");
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Refusing to start: " + String.join(" and ", problems)
					+ (problems.size() == 1 ? " is" : " are")
					+ " unset, blank or the repo's placeholder on a deployed environment."
					+ " Set a long random value in Railway's variables for this environment.");
		}
	}

	private static boolean deployed(PropertyResolver env) {
		return PLATFORM_MARKERS.stream().anyMatch(marker -> {
			String value = env.getProperty(marker);
			return value != null && !value.isBlank();
		});
	}

	private static boolean unusable(String secret, String placeholder) {
		return secret == null || secret.isBlank() || secret.equals(placeholder);
	}
}
