package org.viators.orderprocessingsystem.product.query.entity;

import jakarta.persistence.*;
import lombok.*;
import org.viators.orderprocessingsystem.common.enums.CategoryEnum;
import org.viators.orderprocessingsystem.common.enums.StatusEnum;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-side entity for the Product query model.
 *
 * Key differences from the write entity (ProductWriteEntity):
 *
 * 1. Does NOT extend BaseEntity — the read model doesn't need audit
 *    fields (createdBy, updatedBy) or soft-delete status. It has its
 *    own, simpler schema tailored for reads.
 *
 * 2. No @Version — optimistic locking is a write concern. The read
 *    model is updated by a single event listener, not by concurrent
 *    users. There's no concurrent write conflict to detect.
 *
 * 3. Separate table ("product_read_view") — this is where the CQRS
 *    magic happens. Reads hit this table, writes hit the "product"
 *    table. They can have completely different schemas, indexes,
 *    and even live in different databases.
 *
 * 4. Can contain denormalized/precomputed fields. In this simple
 *    example, the fields mirror the write model. In a real system,
 *    you might add: categoryDisplayName, formattedPrice, averageRating,
 *    reviewCount, searchKeywords — all precomputed by the event
 *    listener so queries never need to join or compute on the fly.
 *
 * 5. Uses @Builder (not @SuperBuilder) since it doesn't extend
 *    BaseEntity. It manages its own id and uuid.
 */
@Entity
@Table(name = "product_read_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier — matches the write model's UUID. */
    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "category", nullable = false)
    private CategoryEnum category;

    @Column(name = "quantity")
    private Long stockQuantity;

    /** When the product was originally created (from the event). */
    @Column(name = "created_at")
    private Instant createdAt;

    /** When the read model was last updated by an event. */
    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusEnum status;
}
