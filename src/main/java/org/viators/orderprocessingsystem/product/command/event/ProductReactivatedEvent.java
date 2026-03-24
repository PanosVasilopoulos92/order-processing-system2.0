package org.viators.orderprocessingsystem.product.command.event;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;

import java.time.Instant;

public record ProductReactivatedEvent(
    String productUuid,
    StatusEnum status,
    Instant updatedAt
) {
}
