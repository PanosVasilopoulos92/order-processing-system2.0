package org.viators.orderprocessingsystem.product.dto.request;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;

public record DeactivateProductRequest(
    String productUuid,
    StatusEnum status
) {
}
