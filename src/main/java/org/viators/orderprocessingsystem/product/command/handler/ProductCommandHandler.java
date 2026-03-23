package org.viators.orderprocessingsystem.product.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.common.enums.StatusEnum;
import org.viators.orderprocessingsystem.exceptions.BusinessValidationException;
import org.viators.orderprocessingsystem.exceptions.DuplicateResourceException;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;
import org.viators.orderprocessingsystem.product.command.event.ProductCreatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductDeactivatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductPriceUpdatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductReactivatedEvent;
import org.viators.orderprocessingsystem.product.command.model.CreateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.DeactivateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.ReactivateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.UpdateProductPriceCommand;
import org.viators.orderprocessingsystem.product.command.repository.ProductCommandRepository;
import org.viators.orderprocessingsystem.product.dto.response.ProductSummaryResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCommandHandler {

    private final ProductCommandRepository productCommandRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductSummaryResponse handle(CreateProductCommand command) {
        if (productCommandRepository.existsByNameIgnoreCase(command.name())) {
            throw new DuplicateResourceException("Product", "name", command.name());
        }

        ProductWriteEntity entity = command.toEntity();
        entity = productCommandRepository.save(entity);

        eventPublisher.publishEvent(new ProductCreatedEvent(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getPrice(),
            entity.getCategory(),
            entity.getStockQuantity(),
            entity.getCreatedAt()
        ));

        return ProductSummaryResponse.from(entity);
    }

    @Transactional
    public ProductSummaryResponse handle(UpdateProductPriceCommand command) {
        ProductWriteEntity product = productCommandRepository
            .findByUuid(command.productUuid())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product", "uuid", command.productUuid()
            ));

        if (product.getPrice().compareTo(command.newPrice()) == 0) {
            throw new IllegalArgumentException(
                "New price must be different from current price: " + product.getPrice()
            );
        }

        // ── Capture old price before mutation for the event ─────────
        var oldPrice = product.getPrice();
        product.setPrice(command.newPrice());
        product = productCommandRepository.save(product);

        // ── Publish event to notify the query side ──────────────────
        // The event is published within the transaction boundary.
        // The listener (annotated with @TransactionalEventListener) will
        // only receive it AFTER this transaction commits successfully.
        // If the save fails and the transaction rolls back, the event
        // is never delivered — which is exactly what we want.
        eventPublisher.publishEvent(new ProductPriceUpdatedEvent(
            product.getUuid(),
            oldPrice,
            product.getPrice(),
            product.getUpdatedAt()
        ));
        log.info("ProductPriceUpdatedEvent published for UUID: {}", product.getUuid());

        return ProductSummaryResponse.from(product);
    }

    @Transactional
    public void handle(DeactivateProductCommand command) {
        ProductWriteEntity entity = productCommandRepository.findByUuid(command.productUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Product", "uuid", command.productUuid()));

        entity.setStatus(StatusEnum.INACTIVE);
        entity = productCommandRepository.save(entity);

        eventPublisher.publishEvent(new ProductDeactivatedEvent(
            entity.getUuid(),
            entity.getStatus(),
            entity.getUpdatedAt()
        ));
    }

    @Transactional
    public void handle(ReactivateProductCommand command) {
        ProductWriteEntity entity = productCommandRepository.findByUuid(command.productUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Product", "uuid", command.productUuid()));

        if (StatusEnum.ACTIVE.equals(entity.getStatus())) {
            throw new BusinessValidationException("Product is already active");
        }

        entity.setStatus(StatusEnum.INACTIVE);
        entity = productCommandRepository.save(entity);

        eventPublisher.publishEvent(new ProductReactivatedEvent(
            entity.getUuid(),
            entity.getStatus(),
            entity.getUpdatedAt()
        ));
    }
}
