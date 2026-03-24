package org.viators.orderprocessingsystem.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.viators.orderprocessingsystem.product.command.model.UpdateProductPriceCommand;

import java.math.BigDecimal;

public record UpdateProductPriceRequest(
    @NotBlank(message = "Product uuid is required")
    String productUuid,

    @NotNull(message = "New price is required")
    BigDecimal newPrice
) {

    public UpdateProductPriceCommand toCommand() {
        return new UpdateProductPriceCommand(
            productUuid,
            newPrice
        );
    }
}
