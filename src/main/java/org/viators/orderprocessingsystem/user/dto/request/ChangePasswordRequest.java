package org.viators.orderprocessingsystem.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.viators.orderprocessingsystem.exceptions.BusinessValidationException;
import org.viators.orderprocessingsystem.user.command.model.ChangePasswordCommand;

public record ChangePasswordRequest(
    @NotBlank(message = "Old password is required")
    @Size(min = 8, max = 100, message = "Password can be between 8-100 characters long")
    String oldPassword,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password can be between 8-100 characters long")
    String newPassword,

    @NotBlank(message = "Password confirmation is required")
    @Size(min = 8, max = 100, message = "Password can be between 8-100 characters long")
    String confirmNewPassword
) {

    public ChangePasswordRequest {
        if (oldPassword.equals(newPassword)) {
            throw new BusinessValidationException("New Password is same with old one");
        }

        if (!newPassword.equals(confirmNewPassword)) {
            throw new BusinessValidationException("Confirm password is not same with new password");
        }
    }

    public ChangePasswordCommand toCommand(String userUuid) {
        return new ChangePasswordCommand(
                userUuid,
                oldPassword,
                newPassword,
                confirmNewPassword
        );
    }
}
