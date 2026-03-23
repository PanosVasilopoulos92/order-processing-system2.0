package org.viators.orderprocessingsystem.user.command.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.viators.orderprocessingsystem.user.command.handler.UserCommandHandler;
import org.viators.orderprocessingsystem.user.command.model.ChangePasswordCommand;
import org.viators.orderprocessingsystem.user.command.model.UpdateEmailCommand;
import org.viators.orderprocessingsystem.user.dto.request.ChangePasswordRequest;
import org.viators.orderprocessingsystem.user.dto.request.UpdateEmailRequest;
import org.viators.orderprocessingsystem.user.dto.response.UserSummaryResponse;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserCommandHandler userCommandHandler;

    public ResponseEntity<UserSummaryResponse> updateEmail(
        @Valid @RequestBody UpdateEmailRequest request
    ) {

        UpdateEmailCommand command = request.toCommand();
        UserSummaryResponse response = userCommandHandler.handleUpdateEmail(command);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<UserSummaryResponse> changePassword(
        @AuthenticationPrincipal(expression = "uuid") String userUUid,
        @Valid @RequestBody ChangePasswordRequest request
    ) {

        ChangePasswordCommand command = request.toCommand(userUUid);
        UserSummaryResponse response = userCommandHandler.handleUpdatePassword(command);
        return ResponseEntity.ok(response);
    }
}
