package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.Role;

public record AuthenticatedUser(Long userId, Role role) {
}
