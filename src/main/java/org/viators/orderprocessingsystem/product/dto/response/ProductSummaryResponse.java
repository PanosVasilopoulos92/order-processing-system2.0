package org.viators.orderprocessingsystem.product.dto.response;

import org.viators.orderprocessingsystem.common.enums.CategoryEnum;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        String uuid,
        String name,
        CategoryEnum category,
        BigDecimal price

) {

    public static ProductSummaryResponse from(ProductWriteEntity product) {
        return new ProductSummaryResponse(
            product.getUuid(),
            product.getName(),
            product.getCategory(),
            product.getPrice()
        );
    }

    public static ProductSummaryResponse from(ProductReadEntity product) {
        return new ProductSummaryResponse(
            product.getUuid(),
            product.getName(),
            product.getCategory(),
            product.getPrice()
        );
    }
}
