package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class JwtFilterChainTest {

	private static final String TEST_JWT_SECRET = "jwt-filter-chain-test-signing-secret-32-bytes!!";

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	@Test
	void noTokenIsRejectedWithStandardEnvelope() throws Exception {
		mockMvc.perform(get("/api/_test/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void malformedTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/_test/protected")
						.header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant past = Instant.now().minusSeconds(3600);
		String expiredToken = Jwts.builder()
				.subject("1")
				.claim("role", Role.MEMBER.name())
				.issuedAt(Date.from(past.minusSeconds(60)))
				.expiration(Date.from(past))
				.signWith(key)
				.compact();

		mockMvc.perform(get("/api/_test/protected")
						.header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void validTokenIsAcceptedAndIdentityIsAvailable() throws Exception {
		String token = jwtService.issue(7L, Role.ADMIN);

		mockMvc.perform(get("/api/_test/protected")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(7))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void healthStillRequiresNoToken() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk());
	}
}
