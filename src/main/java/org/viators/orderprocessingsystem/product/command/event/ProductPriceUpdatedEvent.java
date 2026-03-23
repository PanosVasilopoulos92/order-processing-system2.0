package org.viators.orderprocessingsystem.product.command.event;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductPriceUpdatedEvent(
    String productUuid,
    BigDecimal oldPrice,
    BigDecimal newPrice,
    Instant updatedAt
) {
}
