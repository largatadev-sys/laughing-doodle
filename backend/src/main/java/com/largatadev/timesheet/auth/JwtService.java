package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final Duration ttl;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.ttl-days}") long ttlDays) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		this.ttl = Duration.ofDays(ttlDays);
	}

	public String issue(Long userId, Role role) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("role", role.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)))
				.signWith(signingKey)
				.compact();
	}

	/** Returns empty (never throws) if the token is missing, malformed, unsigned-mismatch, or expired. */
	public Optional<AuthenticatedUser> verify(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			Long userId = Long.valueOf(claims.getSubject());
			Role role = Role.valueOf(claims.get("role", String.class));
			return Optional.of(new AuthenticatedUser(userId, role));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
