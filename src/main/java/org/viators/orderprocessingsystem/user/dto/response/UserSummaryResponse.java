package org.viators.orderprocessingsystem.user.dto.response;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;
import org.viators.orderprocessingsystem.user.UserT;
import org.viators.orderprocessingsystem.user.command.entity.UserWriteEntity;

import java.time.Instant;

public record UserSummaryResponse(
    String username,
    String uuid,
    StatusEnum status
) {

    public static UserSummaryResponse from(UserT user) {
        return new UserSummaryResponse(
            user.getUsername(),
            user.getUuid(),
            user.getStatus()
        );
    }

    public static UserSummaryResponse from(UserWriteEntity user) {
        return new UserSummaryResponse(
                user.getUsername(),
                user.getUuid(),
                user.getStatus()
        );
    }
}
