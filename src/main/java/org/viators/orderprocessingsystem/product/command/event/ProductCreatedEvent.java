package org.viators.orderprocessingsystem.product.command.event;

import org.viators.orderprocessingsystem.common.enums.CategoryEnum;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when a new product is successfully created.
 *
 * This event carries all the data the query side needs to build its
 * read model. The query side should never need to call back to the
 * command side to "look up" additional fields — the event must be
 * self-contained.
 *
 * Why include all fields? The query side may structure its data
 * differently from the command side. By including everything, the
 * event listener can build whatever projection needs without
 * coupling to the write model.
 *
 */
public record ProductCreatedEvent(
    String uuid,
    String name,
    String description,
    BigDecimal price,
    CategoryEnum category,
    Long stockQuantity,
    Instant createdAt
) {

    public ProductReadEntity toEntity() {
        return ProductReadEntity.builder()
            .uuid(uuid)
            .name(name)
            .description(description)
            .price(price)
            .category(category)
            .stockQuantity(stockQuantity)
            .createdAt(createdAt)
            .build();
    }
}
