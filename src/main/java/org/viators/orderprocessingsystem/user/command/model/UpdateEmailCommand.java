package org.viators.orderprocessingsystem.user.command.model;

public record UpdateEmailCommand(
        String userUuid,
        String newEmail
) {
}
