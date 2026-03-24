package org.viators.orderprocessingsystem.product.query.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.viators.orderprocessingsystem.product.dto.response.ProductDetailsResponse;
import org.viators.orderprocessingsystem.product.dto.response.ProductSummaryResponse;
import org.viators.orderprocessingsystem.product.query.handler.ProductQueryHandler;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductQueryController {

    private final ProductQueryHandler productQueryHandler;

    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> getProducts(
        @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ProductSummaryResponse> response = productQueryHandler.handleFindAll(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ProductDetailsResponse> getProductByUuid(
        @PathVariable String uuid
    ) {
        return ResponseEntity.ok(productQueryHandler.handleFindByUuid(uuid));
    }
}
