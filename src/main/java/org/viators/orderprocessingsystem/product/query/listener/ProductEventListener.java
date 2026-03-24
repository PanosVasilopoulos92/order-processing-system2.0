package org.viators.orderprocessingsystem.product.query.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.viators.orderprocessingsystem.product.command.event.ProductCreatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductDeactivatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductPriceUpdatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductReactivatedEvent;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;
import org.viators.orderprocessingsystem.product.query.repository.ProductQueryRepository;

/**
 * Listens for product domain events and updates the read model.
 * <p>
 * This is the bridge between the Command and Query sides of our CQRS
 * architecture. Each event handler translates a domain event into
 * a read model operation.
 * <p>
 * Key annotations explained:
 *
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * Only fires AFTER the command side's transaction commits successfully.
 * If the write fails and rolls back, this listener never executes.
 * This prevents the read model from updating for a write that didn't persist.
 * @Async("cqrsEventExecutor") Runs the listener on the custom thread pool defined in AsyncConfig.
 * This decouples the command side from the query side:
 * - The command returns a response to the client immediately.
 * - The read model update happens in the background.
 * - If the read model update fails, the command side is unaffected.
 * @Transactional(propagation = Propagation.REQUIRES_NEW)
 * Creates a new, independent transaction for the read model update.
 * Why REQUIRES_NEW? Because the command side's transaction is already
 * committed (AFTER_COMMIT). There's no transaction to join. We need
 * our own transaction for the read model's JPA operations.
 * <p>
 * The combination of these three annotations gives us:
 * 1. Guaranteed delivery only for successful writes.
 * 2. Non-blocking command processing.
 * 3. Independent transaction management for the read side.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final ProductQueryRepository productQueryRepository;

    @Async("cqrsEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductCreatedEvent event) {
        log.info("Handling ProductCreatedEvent for UUID: {}", event.uuid());
        ProductReadEntity entity = event.toEntity();

        productQueryRepository.save(entity);
        log.debug("Read model created for product UUID: {}", event.uuid());
    }

    @Async(value = "cqrsEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // Default value
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductPriceUpdatedEvent event) {

        log.info("Handling ProductPriceUpdatedEvent for product UUID: {}", event.productUuid());

        // ── Update only the affected field in the read model ────────
        // We load the read entity, update the price, and save. In a
        // high-throughput system, you might use a direct UPDATE query
        // instead of load-modify-save to skip the SELECT:
        //
        // @Query("UPDATE ProductReadEntity p SET p.price = :price WHERE p.uuid = :uuid")
        // void updatePrice(@Param("uuid") String uuid, @Param("price") BigDecimal price);

        productQueryRepository.findByUuid(event.productUuid())
            .ifPresentOrElse(productReadEntity -> {
                    productReadEntity.setPrice(event.newPrice());
                    productReadEntity.setLastModifiedAt(event.updatedAt());
                    productQueryRepository.save(productReadEntity);
                    log.debug("Read model price updated for UUID: {}", event.productUuid());
                },
                () -> log.warn(
                    "Read model not found for UUID: {}. "
                        + "This may indicate an event ordering issue — "
                        + "the price update arrived before the creation event.",
                    event.productUuid()
                ));
    }

    @Async("cqrsEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductDeactivatedEvent event) {
        productQueryRepository.findByUuid(event.productUuid())
            .ifPresentOrElse(productReadEntity -> {
                    productReadEntity.setStatus(event.status());
                    productReadEntity.setLastModifiedAt(event.updatedAt());
                    productQueryRepository.save(productReadEntity);
                    log.debug("Read model status with uuid: {} has been deactivated", event.productUuid());
                },
                () -> log.warn("Read model not found with uuid: {}", event.productUuid()));
    }

    @Async("cqrsEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductReactivatedEvent event) {
        productQueryRepository.findByUuid(event.productUuid())
            .ifPresentOrElse(productReadEntity -> {
                    productReadEntity.setStatus(event.status());
                    productReadEntity.setLastModifiedAt(event.updatedAt());
                    productQueryRepository.save(productReadEntity);
                    log.debug("Read model status with uuid: {} has been deactivated", event.productUuid());
                },
                () -> log.warn("Read model not found with uuid: {}", event.productUuid()));
    }
}
