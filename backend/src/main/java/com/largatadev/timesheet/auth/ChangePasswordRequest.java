package com.largatadev.timesheet.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
