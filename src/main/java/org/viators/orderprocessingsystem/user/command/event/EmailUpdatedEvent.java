package org.viators.orderprocessingsystem.user.command.event;

import org.viators.orderprocessingsystem.user.query.entity.UserReadEntity;

public record EmailUpdatedEvent(
    String eventUuid,
    String userUuid,
    String username,
    String newEmail
) {

    public void update(UserReadEntity entity) {
        entity.setEmail(newEmail);
    }
}
