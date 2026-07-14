package com.largatadev.timesheet.auth;

import com.largatadev.timesheet.users.UserSummary;

public record LoginResponse(String token, UserSummary user) {
}
