package org.viators.orderprocessingsystem.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.viators.orderprocessingsystem.user.command.model.UpdateEmailCommand;

public record UpdateEmailRequest(
    @NotBlank(message = "User uuid is required")
    String userUuid,

    @NotBlank(message = "Email is required")
    String email
) {

    public UpdateEmailCommand toCommand() {
        return new UpdateEmailCommand(
            userUuid,
            email
        );
    }
}
