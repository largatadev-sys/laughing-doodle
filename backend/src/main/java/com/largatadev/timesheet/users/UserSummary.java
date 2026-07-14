package com.largatadev.timesheet.users;

public record UserSummary(Long id, String name, String username, Role role) {

	public static UserSummary of(User user) {
		return new UserSummary(user.getId(), user.getName(), user.getUsername(), user.getRole());
	}
}
