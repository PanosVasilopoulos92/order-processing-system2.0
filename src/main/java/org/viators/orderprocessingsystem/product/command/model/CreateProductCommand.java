package org.viators.orderprocessingsystem.product.command.model;

import org.viators.orderprocessingsystem.common.enums.CategoryEnum;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Command to create a new product.
 *
 * Why a record? Commands are value objects — they carry data, they're
 * immutable (you shouldn't modify a command after creation), and they
 * need equals/hashCode for logging and debugging. Records give you
 * all of this with zero boilerplate.
 *
 * Why not just use the request DTO directly? Separation of concerns.
 * The request DTO belongs to the API layer (it has @NotBlank, @Positive
 * annotations for HTTP validation). The command belongs to the domain
 * layer — it's what the handler processes. If your product can be created
 * from a REST endpoint, a message queue, or a scheduled job, all three
 * create the same Command object. The DTO is specific to one entry point;
 * the command is universal.
 *
 */
public record CreateProductCommand(
    String name,
    String description,
    BigDecimal price,
    CategoryEnum category,
    Long stockQuantity
) {

    public ProductWriteEntity toEntity() {
        ProductWriteEntity productWriteEntity = new ProductWriteEntity();
        productWriteEntity.setName(name);
        productWriteEntity.setCategory(category);
        productWriteEntity.setPrice(price);
        productWriteEntity.setStockQuantity(stockQuantity);
        Optional.ofNullable(description).ifPresent(productWriteEntity::setDescription);

        return productWriteEntity;
    }
}
