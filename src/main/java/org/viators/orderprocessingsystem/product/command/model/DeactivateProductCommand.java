package org.viators.orderprocessingsystem.product.command.model;

import org.viators.orderprocessingsystem.common.enums.StatusEnum;
import org.viators.orderprocessingsystem.exceptions.BusinessValidationException;

public record DeactivateProductCommand(
    String productUuid,
    StatusEnum status
) {

    public DeactivateProductCommand {
        if (status != null && !StatusEnum.INACTIVE.equals(status)) {
            throw new BusinessValidationException("Wrong status value provided");
        }
    }
}
