package org.viators.orderprocessingsystem.product.command.model;

import java.math.BigDecimal;

public record UpdateProductPriceCommand(
    String productUuid,
    BigDecimal newPrice
) {
}
