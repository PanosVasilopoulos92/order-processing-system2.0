package org.viators.orderprocessingsystem.user.command.model;

public record ChangePasswordCommand(
        String userUuid,
        String oldPassword,
        String newPassword,
        String confirmPassword
) {
}
