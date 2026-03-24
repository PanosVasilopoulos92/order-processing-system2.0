package org.viators.orderprocessingsystem.product.dto.request;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;
import org.viators.orderprocessingsystem.exceptions.BusinessValidationException;

public record ReactivateProductRequest(
    String productUuid,
    StatusEnum status
) {

    public ReactivateProductRequest {
        if (status != null && !StatusEnum.ACTIVE.equals(status)) {
            throw new BusinessValidationException("Wrong status value provided");
        }
    }
}
