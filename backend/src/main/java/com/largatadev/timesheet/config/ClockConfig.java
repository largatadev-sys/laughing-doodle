package com.largatadev.timesheet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The Dashboard's notion of "now": today's boundaries, the default series ranges, and the
 * silent threshold all hang off it. A bean rather than {@code Instant.now()} so the contract
 * tests can pin it — "today in Manila" is only testable against a known instant.
 */
@Configuration
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
