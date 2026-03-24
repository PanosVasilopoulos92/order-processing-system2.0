package org.viators.orderprocessingsystem.product.command.model;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;

public record ReactivateProductCommand(
    String productUuid,
    StatusEnum status
) {
}
