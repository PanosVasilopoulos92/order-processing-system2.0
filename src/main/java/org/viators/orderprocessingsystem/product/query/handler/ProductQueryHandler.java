package org.viators.orderprocessingsystem.product.query.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.product.dto.response.ProductDetailsResponse;
import org.viators.orderprocessingsystem.product.dto.response.ProductSummaryResponse;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;
import org.viators.orderprocessingsystem.product.query.repository.ProductQueryRepository;

/**
 * Handles all product-related read queries.
 *
 * This handler is the Query side's equivalent of the Command handler,
 * but much simpler. Its only job is to fetch data from the read model
 * and map it to response DTOs.
 *
 * Why @Transactional(readOnly = true)?
 * - Tells Hibernate to skip dirty-checking on loaded entities (performance).
 * - Signals to the database driver that it can use a read replica if available.
 * - Documents intent: this service never writes.
 *
 * Why map to DTOs here instead of returning entities?
 * - Decouples the API response format from the database schema.
 * - Prevents accidental lazy-loading exceptions outside the transaction.
 * - Allows different response shapes (summary vs detail) from the same entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductQueryHandler {

    private final ProductQueryRepository productQueryRepository;

    public Page<ProductSummaryResponse> handleFindAll(Pageable pageable) {
        return productQueryRepository.findAllPaginated(pageable)
            .map(ProductSummaryResponse::from);
    }

    public ProductDetailsResponse handleFindByUuid(String productUuid) {
        ProductReadEntity entity = productQueryRepository.findByUuid(productUuid)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product", "uuid", productUuid));

        return ProductDetailsResponse.from(entity);
    }




}
