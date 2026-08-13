package com.largatadev.timesheet.auth;

import java.util.List;

import com.largatadev.timesheet.error.ErrorResponseWriter;
import com.largatadev.timesheet.reports.IntakeSecretFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final List<String> corsAllowedOriginPatterns;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
			@Value("${cors.allowed-origin-patterns}") List<String> corsAllowedOriginPatterns) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
		this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(corsAllowedOriginPatterns);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	/**
	 * The relay intake route, authenticated by a shared secret instead of a JWT (ADR-010).
	 * It is its own chain, ordered ahead of the JWT chain, so the two schemes are physically
	 * separate: the JWT filter never runs here, and this filter never runs on a team route.
	 * A Member's bearer token therefore cannot open intake, and the secret cannot open
	 * anything else.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain intakeFilterChain(
			HttpSecurity http,
			@Value("${reports.intake-secret}") String intakeSecret,
			ErrorResponseWriter errorResponseWriter) throws Exception {

		IntakeSecretFilter intakeSecretFilter = new IntakeSecretFilter(intakeSecret, errorResponseWriter);

		http
				.securityMatcher("/api/intake/**")
				.csrf(AbstractHttpConfigurer::disable)
				// No CORS entry: intake is server-to-server only. A browser must not be able to
				// reach it even with the secret in hand.
				.cors(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.addFilterBefore(intakeSecretFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/health", "/api/auth/login", "/error").permitAll()
						// The API is the security boundary — every /api/** call needs a valid JWT.
						// INV-2 (author-only writes) is enforced downstream in the entries service; unchanged here.
						.requestMatchers("/api/**").authenticated()
						// The bundled Expo web export (static/) is public: it must load before login,
						// holds no secrets, and enforces nothing. See ADR-008.
						.anyRequest().permitAll())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
