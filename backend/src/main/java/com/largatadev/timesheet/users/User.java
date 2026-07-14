package com.largatadev.timesheet.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private Role role;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected User() {
	}

	// Replaces the stored BCrypt hash. The caller is responsible for encoding + verifying the
	// current password first (see AuthService.changePassword).
	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	// Updates the display name (not the login username). Caller validates/trims first.
	public void rename(String newName) {
		this.name = newName;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
