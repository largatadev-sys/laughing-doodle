package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

	private static final String SECRET = "unit-test-signing-secret-at-least-32-bytes-long";

	private final JwtService jwtService = new JwtService(SECRET, 7);

	@Test
	void issuedTokenVerifiesBackToTheSameUserIdAndRole() {
		String token = jwtService.issue(42L, Role.ADMIN);

		Optional<AuthenticatedUser> result = jwtService.verify(token);

		assertThat(result).isPresent();
		assertThat(result.get().userId()).isEqualTo(42L);
		assertThat(result.get().role()).isEqualTo(Role.ADMIN);
	}

	@Test
	void malformedTokenFailsVerification() {
		Optional<AuthenticatedUser> result = jwtService.verify("not-a-real-jwt");

		assertThat(result).isEmpty();
	}

	@Test
	void tokenSignedWithADifferentKeyFailsVerification() {
		SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-signing-secret-32-bytes!".getBytes());
		String foreignToken = Jwts.builder()
				.subject("1")
				.claim("role", "MEMBER")
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(otherKey)
				.compact();

		Optional<AuthenticatedUser> result = jwtService.verify(foreignToken);

		assertThat(result).isEmpty();
	}

	@Test
	void expiredTokenFailsVerification() {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
		Instant past = Instant.now().minusSeconds(3600);
		String expiredToken = Jwts.builder()
				.subject("1")
				.claim("role", "MEMBER")
				.issuedAt(Date.from(past.minusSeconds(60)))
				.expiration(Date.from(past))
				.signWith(key)
				.compact();

		Optional<AuthenticatedUser> result = jwtService.verify(expiredToken);

		assertThat(result).isEmpty();
	}
}
