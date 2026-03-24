package org.viators.orderprocessingsystem.product.command.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.viators.orderprocessingsystem.product.command.handler.ProductCommandHandler;
import org.viators.orderprocessingsystem.product.command.model.CreateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.DeactivateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.ReactivateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.UpdateProductPriceCommand;
import org.viators.orderprocessingsystem.product.dto.request.ReactivateProductRequest;
import org.viators.orderprocessingsystem.product.dto.request.DeactivateProductRequest;
import org.viators.orderprocessingsystem.product.dto.request.UpdateProductPriceRequest;
import org.viators.orderprocessingsystem.product.dto.request.CreateProductRequest;
import org.viators.orderprocessingsystem.product.dto.response.ProductSummaryResponse;

import java.net.URI;

/**
 * REST controller for product write operations (Command side).
 *
 * Notice the URL structure: /api/v1/products for writes. The query
 * controller uses the same base path. This is a deliberate choice —
 * from the client's perspective, it's one resource. The CQRS split
 * is an internal implementation detail. Clients don't need to know
 * about the command/query separation.
 *
 * Alternatively, some teams use /api/v1/commands/products and
 * /api/v1/queries/products to make the split explicit. Both are
 * valid — the choice depends on whether you want to expose the
 * architecture to API consumers.
 *
 * We keep the endpoints unified here to follow standard REST conventions.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductCommandController {

    private final ProductCommandHandler productCommandHandler;

    public ResponseEntity<ProductSummaryResponse> createProduct(
        @Valid @RequestBody CreateProductRequest request) {

        // ── Convert DTO to Command ──────────────────────────────────
        // This mapping step may look trivial now, but it maintains the
        // boundary between API layer and domain layer. The DTO can evolve
        // independently of the command (e.g., API versioning).
        CreateProductCommand command = request.toCommand();

        ProductSummaryResponse response = productCommandHandler.handle(command);

        return ResponseEntity
            .created(URI.create("/api/v1/products/".concat(response.uuid())))
            .body(response);
    }

    public ResponseEntity<ProductSummaryResponse> updateProductPrice(
        @Valid @RequestBody UpdateProductPriceRequest request) {

        UpdateProductPriceCommand command = request.toCommand();
        return ResponseEntity.ok(productCommandHandler.handle(command));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProduct(
        @Valid @RequestBody DeactivateProductRequest request) {

        DeactivateProductCommand command = new DeactivateProductCommand(
            request.productUuid(),
            request.status()
        );

        productCommandHandler.handle(command);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reActivateProduct(
        @Valid @RequestBody ReactivateProductRequest request) {

        ReactivateProductCommand command = new ReactivateProductCommand(
            request.productUuid(),
            request.status()
        );

        productCommandHandler.handle(command);
        return ResponseEntity.noContent().build();
    }
}
