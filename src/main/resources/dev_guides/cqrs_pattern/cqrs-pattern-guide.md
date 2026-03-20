# CQRS Pattern Guide

## For the Order Processing System — Spring Boot 4 / Java 25

---

## Table of Contents

1. [Understanding the Big Picture](#1-understanding-the-big-picture)
2. [How CQRS Works (The Architecture)](#2-how-cqrs-works-the-architecture)
3. [The Mental Model: Why Two Models?](#3-the-mental-model-why-two-models)
4. [When to Use CQRS (And When Not To)](#4-when-to-use-cqrs-and-when-not-to)
5. [Our Practical Example: Product Catalog](#5-our-practical-example-product-catalog)
6. [Maven Dependencies](#6-maven-dependencies)
7. [Application Configuration](#7-application-configuration)
8. [Implementation: The Command Side (Writes)](#8-implementation-the-command-side-writes)
   - 8.1 [Command Write Entity — ProductWriteEntity](#81-command-write-entity--productwriteentity)
   - 8.2 [Command Repository — ProductCommandRepository](#82-command-repository--productcommandrepository)
   - 8.3 [Commands — The Write Intentions](#83-commands--the-write-intentions)
   - 8.4 [Domain Events — What Happened](#84-domain-events--what-happened)
   - 8.5 [Command Handlers — Processing Write Intentions](#85-command-handlers--processing-write-intentions)
   - 8.6 [Command DTOs — Request Validation](#86-command-dtos--request-validation)
   - 8.7 [Command Controller — REST Endpoints for Writes](#87-command-controller--rest-endpoints-for-writes)
9. [Implementation: The Event Bridge](#9-implementation-the-event-bridge)
   - 9.1 [Event Publisher — Broadcasting What Happened](#91-event-publisher--broadcasting-what-happened)
   - 9.2 [Event Listener — Reacting to What Happened](#92-event-listener--reacting-to-what-happened)
10. [Implementation: The Query Side (Reads)](#10-implementation-the-query-side-reads)
    - 10.1 [Query Read Entity — ProductReadEntity](#101-query-read-entity--productreadentity)
    - 10.2 [Query Repository — ProductQueryRepository](#102-query-repository--productqueryrepository)
    - 10.3 [Query Handlers — Processing Read Requests](#103-query-handlers--processing-read-requests)
    - 10.4 [Query DTOs — Response Shaping](#104-query-dtos--response-shaping)
    - 10.5 [Query Controller — REST Endpoints for Reads](#105-query-controller--rest-endpoints-for-reads)
11. [Exception Handling for CQRS](#11-exception-handling-for-cqrs)
12. [How Everything Connects: Request Lifecycle](#12-how-everything-connects-request-lifecycle)
13. [Eventual Consistency: The Trade-Off You Must Understand](#13-eventual-consistency-the-trade-off-you-must-understand)
14. [Testing CQRS Components](#14-testing-cqrs-components)
15. [Testing with cURL](#15-testing-with-curl)
16. [CQRS Best Practices Checklist](#16-cqrs-best-practices-checklist)
17. [Common Mistakes to Avoid](#17-common-mistakes-to-avoid)
18. [What Comes Next: Event Sourcing and Messaging](#18-what-comes-next-event-sourcing-and-messaging)

---

## 1. Understanding the Big Picture

Before writing any code, you need to understand **what problem CQRS solves** and **why separating reads from writes is valuable in distributed systems**.

### The Problem: One Model Serving Two Masters

In a traditional CRUD application, you have a single model (entity) that handles both reads and writes. Your `Product` entity is used to:
- **Write**: validate business rules, enforce invariants, update state, maintain audit trails.
- **Read**: serve list views, search results, detail pages, dashboard aggregations.

This works fine for simple applications. But as your system grows, this single model becomes a compromise:

- **Write requirements pull in one direction**: you need rich domain logic, validation, optimistic locking, audit trails, and normalized data (3NF) to avoid update anomalies.
- **Read requirements pull in the opposite direction**: you need denormalized data (pre-joined), computed fields, different projections for different consumers (mobile app wants a summary, admin dashboard wants full details), and optimized query performance.

When you try to serve both from one model, you end up with an entity that's too bloated for writes and too constrained for reads. Your `Product` entity has 30 fields, but your product list only needs 5 of them, plus a computed field that requires joining 3 tables.

### The Solution: Command Query Responsibility Segregation (CQRS)

CQRS says: **split your single model into two — one optimized for writes (the Command model) and one optimized for reads (the Query model).**

The name tells you exactly what it does:
- **Command** = an intention to change state ("create this product", "update the price").
- **Query** = a request to read state ("give me all active products", "show me product details").
- **Responsibility Segregation** = these two responsibilities are handled by separate models, services, and sometimes even separate databases.

Think of it like a restaurant. The **kitchen** (command side) is optimized for preparing food — ingredients stored for efficient cooking, recipes enforcing quality standards, chefs coordinating complex operations. The **menu and display counter** (query side) is optimized for customer browsing — items organized by category, photos showing the final dish, prices pre-calculated with tax. The kitchen and the display are completely different systems optimized for completely different purposes, but they stay synchronized: when the kitchen creates a new dish, the menu gets updated.

### Where This Fits in Your Learning Journey

CQRS is a **standalone pattern** — it does not require Event Sourcing, Kafka, or microservices. Many teams adopt CQRS within a single monolithic application and a single database. This guide covers exactly that: pure CQRS with Spring's in-process event system (`ApplicationEventPublisher`). Event Sourcing and message brokers are separate patterns that compose well with CQRS, but they are **not prerequisites**.

---

## 2. How CQRS Works (The Architecture)

### Traditional CRUD (Single Model)
```
Client
  |
  |  POST /api/v1/products        GET /api/v1/products
  |  (Create a product)           (List all products)
  |           |                           |
  |           v                           v
  |   ┌─────────────────────────────────────────┐
  |   │           ProductController              │
  |   │  (One controller handles both)           │
  |   └─────────────────┬───────────────────────┘
  |                     |
  |                     v
  |   ┌─────────────────────────────────────────┐
  |   │            ProductService                │
  |   │  (One service with save + find methods)  │
  |   └─────────────────┬───────────────────────┘
  |                     |
  |                     v
  |   ┌─────────────────────────────────────────┐
  |   │           ProductRepository              │
  |   │  (One repository, one entity)            │
  |   └─────────────────┬───────────────────────┘
  |                     |
  |                     v
  |              ┌──────────────┐
  |              │  Product DB  │
  |              │  (One table) │
  |              └──────────────┘
```

### CQRS Architecture (Separate Models)
```
Client
  |
  |  POST /api/v1/products              GET /api/v1/products
  |  (Create a product)                 (List all products)
  |           |                                  |
  |           v                                  v
  |   ┌──────────────────┐             ┌──────────────────┐
  |   │ ProductCommand   │             │ ProductQuery     │
  |   │ Controller       │             │ Controller       │
  |   └────────┬─────────┘             └────────┬─────────┘
  |            |                                |
  |            v                                v
  |   ┌──────────────────┐             ┌──────────────────┐
  |   │ ProductCommand   │             │ ProductQuery     │
  |   │ Handler          │             │ Handler          │
  |   └────────┬─────────┘             └────────┬─────────┘
  |            |                                |
  |            v                                v
  |   ┌──────────────────┐             ┌──────────────────┐
  |   │ ProductCommand   │             │ ProductQuery     │
  |   │ Repository       │             │ Repository       │
  |   └────────┬─────────┘             └────────┬─────────┘
  |            |                                |
  |            v                                v
  |   ┌──────────────────┐             ┌──────────────────┐
  |   │  Write Model     │             │  Read Model      │
  |   │  (Normalized)    │  ──Event──> │  (Denormalized)  │
  |   └──────────────────┘             └──────────────────┘
  |
  |  The write model publishes events after state changes.
  |  The read model listens to events and updates its projection.
```

**Key insight**: the two sides communicate through **events**. When the command side changes state, it publishes an event ("ProductCreated", "ProductPriceUpdated"). The query side listens for those events and updates its own read-optimized model. This is what makes the two sides eventually consistent — there's a brief moment between the write and the read model update where the data is out of sync.

---

## 3. The Mental Model: Why Two Models?

### Reads and Writes Have Fundamentally Different Needs

| Concern | Command Side (Writes) | Query Side (Reads) |
|---------|----------------------|-------------------|
| **Data shape** | Normalized (3NF) — no duplication | Denormalized — pre-joined, pre-computed |
| **Validation** | Full business rule enforcement | None — data is already validated |
| **Concurrency** | Optimistic locking (`@Version`) | No locking needed — reads don't conflict |
| **Performance goal** | Correctness over speed | Speed over everything |
| **Scaling** | Scale modestly (writes are less frequent) | Scale aggressively (reads dominate traffic) |
| **Schema changes** | Rare, carefully migrated | Flexible — rebuild projections anytime |

### A Concrete Example

Imagine your product list page needs to show:
- Product name, price, category name, average rating, total reviews count.

In a traditional CRUD model, this requires joining `product`, `category`, and `review` tables on every request. With 10,000 products and 500,000 reviews, this join gets expensive.

With CQRS, the read model **stores the pre-computed result**. The `product_read` table already has `category_name`, `average_rating`, and `review_count` as columns. No joins needed — it's a simple `SELECT` from a flat table. When a new review is posted, an event updates the `average_rating` and `review_count` in the read model.

### The Read Model is a "Projection"

This is a critical concept. The read model is not the source of truth — it's a **projection** (or "view") derived from events produced by the write model. You can think of it like a materialized view in a database, except you control exactly what data it contains and how it's structured.

This means:
- You can have **multiple projections** of the same data (one for the mobile app, one for the admin dashboard).
- If a projection gets corrupted or needs to change, you can **rebuild it** by replaying events (this is where Event Sourcing shines, covered in a separate guide).
- Projections are **disposable** — they are never the primary source of truth.

---

## 4. When to Use CQRS (And When Not To)

CQRS adds architectural complexity. Not every application needs it.

### Use CQRS When

- **Read and write patterns are significantly different** — different shapes, different frequencies, different performance requirements.
- **Read performance is critical** — you need to serve complex aggregated data fast, and joins are becoming a bottleneck.
- **Multiple read representations exist** — different consumers need different views of the same data.
- **You need independent scalability** — reads outnumber writes 100:1, and you want to scale the read side separately.
- **You're building toward event-driven architecture** — CQRS is the natural stepping stone to Event Sourcing and distributed microservices.

### Do NOT Use CQRS When

- **Simple CRUD is sufficient** — if your read and write models would look nearly identical, CQRS adds complexity without benefit.
- **Strong consistency is non-negotiable** — CQRS introduces eventual consistency between write and read models. If your domain requires every read to reflect the absolute latest write (e.g., bank account balance during a transfer), CQRS requires careful handling.
- **Small team, early-stage product** — start with CRUD, refactor to CQRS when the pain points emerge. Premature CQRS is over-engineering.

### Our Situation

For this guide, we're using CQRS because:
1. It's the pattern you're learning as part of your Event-Driven Microservices journey.
2. The Product Catalog domain naturally has different read/write needs — writes enforce business rules while reads serve optimized product listings.
3. It establishes the foundation for Event Sourcing and message brokers in future guides.

---

## 5. Our Practical Example: Product Catalog

We'll implement a simple Product Catalog with CQRS. The business logic is intentionally simple so the focus stays on the architecture.

### What the System Does

**Command Side (Writes)**:
- **Create a product** — with name, description, price, and category.
- **Update a product's price** — an intentionally separate command to demonstrate that commands represent specific intentions, not generic "update everything" operations.

**Query Side (Reads)**:
- **List all active products** — a flat, denormalized view optimized for browsing.
- **Get product details by UUID** — a single product's full read model.

### Why Separate "Update Price" from a Generic "Update"?

This is a core CQRS principle: **commands express business intentions, not CRUD operations**. "Update product" is vague — what changed? The name? The price? The description? Each of these might have different validation rules, trigger different events, and update different parts of the read model.

By naming the command `UpdateProductPriceCommand`, the code communicates exactly what business operation is happening. It also means the event (`ProductPriceUpdatedEvent`) tells the query side exactly which fields to update in the read model, instead of replacing everything.

---

## 6. Maven Dependencies

No additional dependencies are needed beyond what your project already has. CQRS is an architectural pattern — it's implemented with standard Spring components:

```xml
<!-- You already have these in your pom.xml -->

<!-- Spring Boot Starter Web — REST controllers, Jackson serialization -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Boot Starter Data JPA — repositories, entity management -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Spring Boot Starter Validation — @NotBlank, @Positive, etc. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Lombok — boilerplate reduction -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**Why no special CQRS library?** Frameworks like Axon Framework exist and provide CQRS/Event Sourcing infrastructure out of the box. We intentionally avoid them in this guide because:
1. Understanding the raw mechanics teaches you more than a framework-magic approach.
2. You can always adopt a framework later once you understand what it's abstracting away.
3. Spring's built-in `ApplicationEventPublisher` is sufficient for in-process CQRS.

---

## 7. Application Configuration

No special CQRS-specific configuration is needed in `application.yaml`. Your existing JPA and database configuration handles both the write and read models since they live in the same PostgreSQL database (separate tables).

However, we do need to ensure Spring's event system is enabled for async processing. Add this configuration class:

```java
package org.viators.orderprocessingsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures asynchronous event processing for the CQRS event bridge.
 *
 * Why async? In CQRS, the command side publishes events after a successful
 * write operation. The query side (event listener) processes those events
 * to update the read model. If event processing is synchronous, a failure
 * in the read model update would roll back the write transaction — breaking
 * the independence of the two sides.
 *
 * With @EnableAsync, event listeners annotated with @Async run on a separate
 * thread pool. The command side completes its transaction independently,
 * and the query side processes events on its own timeline.
 *
 * Trade-off: This introduces eventual consistency. The read model may be
 * briefly out of date after a write. For most use cases (product catalogs,
 * dashboards, search results), this is perfectly acceptable.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Custom thread pool for CQRS event processing.
     *
     * Why not use the default SimpleAsyncTaskExecutor? The default creates
     * a new thread for every async invocation — no pooling, no queue, no
     * backpressure. In production, a burst of events could spawn thousands
     * of threads and crash the JVM. A ThreadPoolTaskExecutor provides:
     * - Bounded thread count (core + max)
     * - A queue to buffer events during spikes
     * - Graceful shutdown with awaitTermination
     *
     * @return a configured thread pool executor for async event handling
     */
    @Bean(name = "cqrsEventExecutor")
    public Executor cqrsEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads: always alive, ready to process events immediately.
        // Start with 2 — enough for typical event throughput in a monolith.
        executor.setCorePoolSize(2);

        // Max threads: upper bound during event spikes.
        // Only created when the queue is full.
        executor.setMaxPoolSize(5);

        // Queue capacity: buffer events when all core threads are busy.
        // 100 is generous for in-process events. If you regularly exceed this,
        // it's a sign you should move to a message broker (Kafka, RabbitMQ).
        executor.setQueueCapacity(100);

        // Thread name prefix: makes thread dumps readable.
        // You'll see "cqrs-event-1", "cqrs-event-2" in logs and dumps.
        executor.setThreadNamePrefix("cqrs-event-");

        // Wait for queued events to finish during application shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
```

### Why Not Use `@TransactionalEventListener` Instead of `@Async`?

Good question — both are valid approaches. `@TransactionalEventListener` fires after the transaction commits (guaranteeing the write succeeded), but runs **synchronously on the same thread**. If the read model update fails, it throws an exception on the command thread.

We use `@Async` + `@TransactionalEventListener` together: the `@TransactionalEventListener` ensures events fire only after a successful commit, and `@Async` ensures the processing happens on a separate thread. This gives us both guarantees: the write succeeded AND the read model update doesn't block or fail the command side. We'll see this combination in action in section 9.

---

## 8. Implementation: The Command Side (Writes)

The command side is responsible for **enforcing business rules and persisting state changes**. It is the source of truth. Every write goes through here, and after each successful write, an event is published to notify the query side.

### 8.1 Command Write Entity — ProductWriteEntity

This is your write model — the normalized, JPA-managed entity that represents the product in the database. It contains all fields needed for business logic, validation, and auditing.

```java
package org.viators.orderprocessingsystem.product.command.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.viators.orderprocessingsystem.common.BaseEntity;

import java.math.BigDecimal;

/**
 * Write-side entity for the Product aggregate.
 *
 * This entity is part of the Command model in our CQRS architecture.
 * It is the source of truth for product data and is optimized for
 * enforcing business rules, not for read performance.
 *
 * Key design decisions:
 * - Extends BaseEntity for id, uuid, createdAt, updatedAt, status fields.
 * - Uses @Version for optimistic locking — critical when multiple users
 *   might update the same product concurrently.
 * - Uses BigDecimal for price — never use double/float for monetary values
 *   due to floating-point precision errors (0.1 + 0.2 != 0.3 in IEEE 754).
 * - Maps to "product" table — the query side maps to a separate
 *   "product_read_view" table.
 */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductWriteEntity extends BaseEntity {

    /** Product display name. Business rule: must be unique and non-empty. */
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    /** Detailed product description. Optional but recommended. */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Product price in the system's base currency.
     *
     * Why BigDecimal? Financial calculations require exact precision.
     * A double would introduce rounding errors:
     *   double result = 0.1 + 0.2; // 0.30000000000000004
     *   BigDecimal result = new BigDecimal("0.1").add(new BigDecimal("0.2")); // 0.3
     *
     * Why precision 10, scale 2? Supports prices up to 99,999,999.99 — more
     * than sufficient for a product catalog. Adjust if your domain needs
     * sub-cent precision (e.g., stock trading uses scale 4+).
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Product category for organization and filtering. */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Optimistic locking version field.
     *
     * How it works: JPA reads the version when loading the entity.
     * On update, it adds "WHERE version = :loadedVersion" to the UPDATE SQL.
     * If another transaction incremented the version in between, the WHERE
     * matches zero rows, and JPA throws OptimisticLockException.
     *
     * Why this matters for CQRS: The write model is the source of truth.
     * Concurrent writes must be detected and handled (fail-fast), not silently
     * overwrite each other (last-write-wins). The read model, being a
     * projection, doesn't need locking — it's updated by events, not by users.
     */
    @Version
    @Column(name = "version")
    private Long version;
}
```

**Important**: Notice this entity maps to the `product` table — the standard, normalized table. The query side will map to a different table (`product_read_view`). Both tables live in the same database for this guide. In a more advanced setup, they could be in separate databases entirely.

---

### 8.2 Command Repository — ProductCommandRepository

The command repository only needs methods that support write operations. No complex queries, no sorting, no pagination — those belong on the query side.

```java
package org.viators.orderprocessingsystem.product.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;

import java.util.Optional;

/**
 * Repository for write operations on the Product aggregate.
 *
 * Notice how lean this interface is compared to a typical CRUD repository.
 * In CQRS, the command repository only needs:
 * - save() — inherited from JpaRepository
 * - findByUuid() — to load an entity for updates
 * - existsByName() — to enforce uniqueness before insert
 *
 * There are no findAll(), no pagination, no sorting, no @Query methods
 * with complex joins. All of that belongs in the Query repository.
 * This separation keeps each side focused on its single responsibility.
 */
@Repository
public interface ProductCommandRepository extends JpaRepository<ProductWriteEntity, Long> {

    /**
     * Finds a product by its public UUID.
     *
     * Used when processing update commands — the client sends a UUID
     * (never the internal Long id), and we need to load the entity
     * to apply business rules and persist changes.
     *
     * @param uuid the public-facing product identifier
     * @return the product if found, empty otherwise
     */
    Optional<ProductWriteEntity> findByUuid(String uuid);

    /**
     * Checks if a product name is already taken.
     *
     * Called before creating a new product to provide a clear error
     * message rather than letting the DB throw a constraint violation.
     *
     * @param name the product name to check
     * @return true if a product with this name already exists
     */
    boolean existsByName(String name);
}
```

---

### 8.3 Commands — The Write Intentions

A **Command** is an explicit instruction to change state. It's not a DTO — it's a domain concept that carries both the data and the intent. Commands are named as imperatives: "CreateProduct", "UpdateProductPrice" — they tell the system to **do something**.

```java
package org.viators.orderprocessingsystem.product.command.model;

import java.math.BigDecimal;

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
 * @param name        the product display name
 * @param description optional product description
 * @param price       the product price (must be positive)
 * @param category    the product category
 */
public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        String category
) {}
```

```java
package org.viators.orderprocessingsystem.product.command.model;

import java.math.BigDecimal;

/**
 * Command to update an existing product's price.
 *
 * Notice this command is specific: it's "update price", not "update product".
 * This is a deliberate CQRS practice — commands should represent specific
 * business intentions. Benefits:
 *
 * 1. The handler knows exactly what validation to apply (is the new price
 *    positive? Is it different from the current price?).
 * 2. The resulting event (ProductPriceUpdatedEvent) tells the query side
 *    exactly what changed — no need to diff the entire entity.
 * 3. It creates a clear audit trail: "User X changed the price from $10
 *    to $15" vs "User X updated the product" (updated what?).
 *
 * @param productUuid the UUID of the product to update
 * @param newPrice    the new price to set
 */
public record UpdateProductPriceCommand(
        String productUuid,
        BigDecimal newPrice
) {}
```

---

### 8.4 Domain Events — What Happened

An **Event** is a statement of fact about something that already happened. Events are named in past tense: "ProductCreated", "ProductPriceUpdated" — they tell the system **what occurred**.

The distinction between commands and events is crucial:
- A **command** can be rejected (invalid data, business rule violation).
- An **event** is a fact — it already happened, it cannot be rejected.

```java
package org.viators.orderprocessingsystem.product.command.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * event listener can build whatever projection it needs without
 * coupling to the write model.
 *
 * Why LocalDateTime instead of Instant? Our BaseEntity uses
 * LocalDateTime for createdAt/updatedAt, so we stay consistent.
 * In a distributed system with multiple time zones, Instant would
 * be the better choice — but that's a concern for the microservices
 * guide.
 *
 * @param uuid        the product's public identifier
 * @param name        the product display name
 * @param description the product description
 * @param price       the product price
 * @param category    the product category
 * @param createdAt   when the product was created
 */
public record ProductCreatedEvent(
        String uuid,
        String name,
        String description,
        BigDecimal price,
        String category,
        LocalDateTime createdAt
) {}
```

```java
package org.viators.orderprocessingsystem.product.command.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a product's price is updated.
 *
 * Notice this only carries the fields relevant to the change:
 * the product UUID (to identify which product), the old price
 * (for audit purposes), the new price, and the timestamp.
 *
 * Including the old price serves two purposes:
 * 1. The event listener can log the change ("price changed from $10 to $15").
 * 2. If we later add event-sourced projections, the old value is available
 *    for computing deltas without replaying the full event history.
 *
 * @param productUuid the UUID of the updated product
 * @param oldPrice    the previous price (for audit/logging)
 * @param newPrice    the updated price
 * @param updatedAt   when the change occurred
 */
public record ProductPriceUpdatedEvent(
        String productUuid,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        LocalDateTime updatedAt
) {}
```

---

### 8.5 Command Handlers — Processing Write Intentions

The **Command Handler** is where business logic lives. It receives a command, validates business rules, persists the change, and publishes the resulting event. Each handler focuses on one command — this keeps the logic clear and testable.

```java
package org.viators.orderprocessingsystem.product.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.common.enums.StatusEnum;
import org.viators.orderprocessingsystem.exceptions.DuplicateResourceException;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;
import org.viators.orderprocessingsystem.product.command.event.ProductCreatedEvent;
import org.viators.orderprocessingsystem.product.command.event.ProductPriceUpdatedEvent;
import org.viators.orderprocessingsystem.product.command.model.CreateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.UpdateProductPriceCommand;
import org.viators.orderprocessingsystem.product.command.repository.ProductCommandRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles all product-related write commands.
 *
 * This is the core of the Command side. Each method:
 * 1. Validates business rules (not just input validation — that's the DTO's job).
 * 2. Persists the state change to the write model (source of truth).
 * 3. Publishes a domain event so the query side can update its projection.
 *
 * Why is event publishing inside the handler, not the entity?
 * The entity is a JPA-managed object — it should focus on data and invariants.
 * Event publishing is an application-level concern (it involves Spring's
 * ApplicationEventPublisher). Keeping it in the handler makes the flow
 * explicit and testable: you can mock the publisher in unit tests.
 *
 * Why @Transactional on each method?
 * Each command is a unit of work. If anything fails (validation, persistence),
 * the entire operation rolls back. The event is published within the transaction
 * but processed after it commits (via @TransactionalEventListener on the
 * listener side). This ensures events are only published for successful writes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCommandHandler {

    private final ProductCommandRepository productCommandRepository;

    /**
     * Spring's in-process event publisher. In a future microservices setup,
     * this would be replaced with a message broker (Kafka, RabbitMQ) — but
     * the handler's interface wouldn't change. That's the power of coding
     * against an abstraction.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Handles the creation of a new product.
     *
     * Business rules enforced:
     * - Product name must be unique (duplicate detection).
     *
     * @param command the create product command
     * @return the UUID of the newly created product
     * @throws DuplicateResourceException if a product with the same name exists
     */
    @Transactional
    public String handle(CreateProductCommand command) {
        log.info("Handling CreateProductCommand for product: {}", command.name());

        // ── Business Rule: Product name must be unique ──────────────
        // We check before saving to provide a meaningful error message.
        // The DB unique constraint is the safety net, but we don't want
        // to rely on catching ConstraintViolationException — that's ugly
        // and database-specific.
        if (productCommandRepository.existsByName(command.name())) {
            throw new DuplicateResourceException(
                    "Product", "name", command.name()
            );
        }

        // ── Build and persist the write entity ──────────────────────
        ProductWriteEntity product = ProductWriteEntity.builder()
                .uuid(UUID.randomUUID().toString())
                .name(command.name())
                .description(command.description())
                .price(command.price())
                .category(command.category())
                .status(StatusEnum.ACTIVE)
                .build();

        ProductWriteEntity savedProduct = productCommandRepository.save(product);
        log.debug("Product persisted with UUID: {}", savedProduct.getUuid());

        // ── Publish event to notify the query side ──────────────────
        // The event is published within the transaction boundary.
        // The listener (annotated with @TransactionalEventListener) will
        // only receive it AFTER this transaction commits successfully.
        // If the save fails and the transaction rolls back, the event
        // is never delivered — which is exactly what we want.
        eventPublisher.publishEvent(new ProductCreatedEvent(
                savedProduct.getUuid(),
                savedProduct.getName(),
                savedProduct.getDescription(),
                savedProduct.getPrice(),
                savedProduct.getCategory(),
                savedProduct.getCreatedAt()
        ));

        log.info("ProductCreatedEvent published for UUID: {}", savedProduct.getUuid());
        return savedProduct.getUuid();
    }

    /**
     * Handles updating a product's price.
     *
     * Business rules enforced:
     * - Product must exist and be active.
     * - New price must be different from current price (no-op prevention).
     *
     * @param command the update price command
     * @throws ResourceNotFoundException if the product doesn't exist
     * @throws IllegalArgumentException if the new price equals the current price
     */
    @Transactional
    public void handle(UpdateProductPriceCommand command) {
        log.info("Handling UpdateProductPriceCommand for product: {}", command.productUuid());

        // ── Load the entity ─────────────────────────────────────────
        // findByUuid loads the entity into the persistence context.
        // JPA's dirty checking will detect changes at flush time.
        // The @Version field enables optimistic locking — if another
        // transaction modified this product between our read and write,
        // JPA throws OptimisticLockException.
        ProductWriteEntity product = productCommandRepository
                .findByUuid(command.productUuid())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "uuid", command.productUuid()
                ));

        // ── Business Rule: Price must actually change ───────────────
        // compareTo is used instead of equals for BigDecimal because
        // equals considers scale: new BigDecimal("10.0").equals(new
        // BigDecimal("10.00")) returns false. compareTo compares
        // numeric value only.
        if (product.getPrice().compareTo(command.newPrice()) == 0) {
            throw new IllegalArgumentException(
                    "New price must be different from current price: " + product.getPrice()
            );
        }

        // ── Capture old price before mutation for the event ─────────
        var oldPrice = product.getPrice();

        // ── Apply the change ────────────────────────────────────────
        product.setPrice(command.newPrice());
        // No explicit save() needed — JPA dirty checking detects the change
        // and flushes it at transaction commit. We call save() anyway for
        // clarity and to get the updated entity back.
        productCommandRepository.save(product);

        // ── Publish event ───────────────────────────────────────────
        eventPublisher.publishEvent(new ProductPriceUpdatedEvent(
                product.getUuid(),
                oldPrice,
                command.newPrice(),
                LocalDateTime.now()
        ));

        log.info("ProductPriceUpdatedEvent published for UUID: {}", product.getUuid());
    }
}
```

---

### 8.6 Command DTOs — Request Validation

These DTOs validate the incoming HTTP request before it reaches the command handler. They are the API layer's concern — Bean Validation annotations like `@NotBlank` and `@Positive` catch malformed input early, before any business logic runs.

```java
package org.viators.orderprocessingsystem.product.command.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for creating a product via the REST API.
 *
 * Why a record? Request DTOs are immutable value objects — you receive
 * them, validate them, and convert them to commands. They should never
 * be modified after deserialization.
 *
 * Why separate from CreateProductCommand? Different responsibilities:
 * - This DTO handles HTTP-level validation (@NotBlank, @Size).
 * - The Command is a domain concept used by the handler.
 * - If you add a message queue consumer later, it creates the same
 *   Command from a different input format — no DTO involved.
 *
 * @param name        the product name (required, 1-150 chars)
 * @param description optional product description (max 1000 chars)
 * @param price       the product price (required, must be positive)
 * @param category    the product category (required)
 */
public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name must not exceed 150 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Positive(message = "Price must be a positive value")
        BigDecimal price,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must not exceed 50 characters")
        String category
) {}
```

```java
package org.viators.orderprocessingsystem.product.command.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for updating a product's price via the REST API.
 *
 * @param newPrice the new price to set (required, must be positive)
 */
public record UpdateProductPriceRequest(

        @NotNull(message = "New price is required")
        @Positive(message = "Price must be a positive value")
        BigDecimal newPrice
) {}
```

---

### 8.7 Command Controller — REST Endpoints for Writes

The command controller exposes REST endpoints for write operations. It receives HTTP requests, validates the input (via `@Valid`), converts DTOs to commands, and delegates to the command handler.

```java
package org.viators.orderprocessingsystem.product.command.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.viators.orderprocessingsystem.product.command.dto.request.CreateProductRequest;
import org.viators.orderprocessingsystem.product.command.dto.request.UpdateProductPriceRequest;
import org.viators.orderprocessingsystem.product.command.handler.ProductCommandHandler;
import org.viators.orderprocessingsystem.product.command.model.CreateProductCommand;
import org.viators.orderprocessingsystem.product.command.model.UpdateProductPriceCommand;

import java.net.URI;
import java.util.Map;

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

    /**
     * Creates a new product.
     *
     * @param request the validated product creation request
     * @return 201 Created with the new product's UUID and location header
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        // ── Convert DTO to Command ──────────────────────────────────
        // This mapping step may look trivial now, but it maintains the
        // boundary between API layer and domain layer. The DTO can evolve
        // independently from the command (e.g., API versioning).
        var command = new CreateProductCommand(
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );

        String uuid = productCommandHandler.handle(command);

        // ── Return 201 Created ──────────────────────────────────────
        // REST convention: POST that creates a resource returns 201
        // with a Location header pointing to the new resource.
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + uuid))
                .body(Map.of("uuid", uuid, "message", "Product created successfully"));
    }

    /**
     * Updates a product's price.
     *
     * Why PATCH instead of PUT?
     * - PUT replaces the entire resource — you'd need to send all fields.
     * - PATCH applies a partial update — only the fields that change.
     * Since we're only updating the price, PATCH is semantically correct.
     *
     * @param uuid    the product's public identifier
     * @param request the validated price update request
     * @return 200 OK with a confirmation message
     */
    @PatchMapping("/{uuid}/price")
    public ResponseEntity<Map<String, String>> updateProductPrice(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateProductPriceRequest request
    ) {
        var command = new UpdateProductPriceCommand(uuid, request.newPrice());
        productCommandHandler.handle(command);

        return ResponseEntity.ok(
                Map.of("message", "Product price updated successfully")
        );
    }
}
```

---

## 9. Implementation: The Event Bridge

The event bridge connects the command side to the query side. When the command side publishes an event, the bridge delivers it to the query side so it can update its read model. In this guide, we use Spring's built-in `ApplicationEventPublisher` — an in-process, JVM-level event system.

### 9.1 Event Publisher — Broadcasting What Happened

The event publisher is already built into the command handler (section 8.5). When we call `eventPublisher.publishEvent(...)`, Spring broadcasts the event to all registered listeners. No additional publisher class is needed.

In a future guide with Kafka or RabbitMQ, you would swap `ApplicationEventPublisher` with a message broker client. The command handler's code would barely change — you'd still call `publish(event)`, just through a different implementation.

### 9.2 Event Listener — Reacting to What Happened

The event listener sits on the query side and reacts to events published by the command side. It transforms each event into a read model update.

```java
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
import org.viators.orderprocessingsystem.product.command.event.ProductPriceUpdatedEvent;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;
import org.viators.orderprocessingsystem.product.query.repository.ProductQueryRepository;

/**
 * Listens for product domain events and updates the read model.
 *
 * This is the bridge between the Command and Query sides of our CQRS
 * architecture. Each event handler translates a domain event into
 * a read model operation.
 *
 * Key annotations explained:
 *
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *   Only fires AFTER the command side's transaction commits successfully.
 *   If the write fails and rolls back, this listener never executes.
 *   This prevents the read model from updating for a write that didn't persist.
 *
 * @Async("cqrsEventExecutor")
 *   Runs the listener on the custom thread pool defined in AsyncConfig.
 *   This decouples the command side from the query side:
 *   - The command returns a response to the client immediately.
 *   - The read model update happens in the background.
 *   - If the read model update fails, the command side is unaffected.
 *
 * @Transactional(propagation = Propagation.REQUIRES_NEW)
 *   Creates a new, independent transaction for the read model update.
 *   Why REQUIRES_NEW? Because the command side's transaction is already
 *   committed (AFTER_COMMIT). There's no transaction to join. We need
 *   our own transaction for the read model's JPA operations.
 *
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

    /**
     * Handles the ProductCreatedEvent by creating a new read model entry.
     *
     * @param event the product creation event from the command side
     */
    @Async("cqrsEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductCreatedEvent event) {
        log.info("Handling ProductCreatedEvent for UUID: {}", event.uuid());

        // ── Build the read model from the event data ────────────────
        // The read entity is denormalized — it may contain pre-computed
        // or pre-formatted fields that don't exist in the write model.
        // For this simple example, the fields are similar, but imagine
        // adding averageRating, reviewCount, or a formatted price string.
        ProductReadEntity readEntity = ProductReadEntity.builder()
                .uuid(event.uuid())
                .name(event.name())
                .description(event.description())
                .price(event.price())
                .category(event.category())
                .createdAt(event.createdAt())
                .build();

        productQueryRepository.save(readEntity);
        log.debug("Read model created for product UUID: {}", event.uuid());
    }

    /**
     * Handles the ProductPriceUpdatedEvent by updating the read model's price.
     *
     * @param event the price update event from the command side
     */
    @Async("cqrsEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProductPriceUpdatedEvent event) {
        log.info("Handling ProductPriceUpdatedEvent for UUID: {} ({} -> {})",
                event.productUuid(), event.oldPrice(), event.newPrice());

        // ── Update only the affected field in the read model ────────
        // We load the read entity, update the price, and save. In a
        // high-throughput system, you might use a direct UPDATE query
        // instead of load-modify-save to skip the SELECT:
        //
        //   @Query("UPDATE ProductReadEntity p SET p.price = :price WHERE p.uuid = :uuid")
        //   void updatePrice(@Param("uuid") String uuid, @Param("price") BigDecimal price);
        //
        // For now, the load-modify-save approach is clearer and sufficient.
        productQueryRepository.findByUuid(event.productUuid())
                .ifPresentOrElse(
                        readEntity -> {
                            readEntity.setPrice(event.newPrice());
                            readEntity.setLastModifiedAt(event.updatedAt());
                            productQueryRepository.save(readEntity);
                            log.debug("Read model price updated for UUID: {}", event.productUuid());
                        },
                        () -> log.warn(
                                "Read model not found for UUID: {}. "
                                + "This may indicate an event ordering issue — "
                                + "the price update arrived before the creation event.",
                                event.productUuid()
                        )
                );
    }
}
```

**Critical design note**: The `log.warn` in the `ifPresentOrElse` handles a real scenario — **event ordering**. In an async system, it's theoretically possible (though rare with in-process events) for the `ProductPriceUpdatedEvent` to be processed before the `ProductCreatedEvent`. In a production system with a message broker, you'd handle this with retry mechanisms or event ordering guarantees. For now, logging the anomaly is the right first step.

---

## 10. Implementation: The Query Side (Reads)

The query side is responsible for **serving read-optimized data as fast as possible**. It has no business logic, no validation, no write operations. It simply reads from a projection that was built by the event listener.

### 10.1 Query Read Entity — ProductReadEntity

This is your read model — a denormalized entity optimized for fast queries. It lives in a separate table from the write model.

```java
package org.viators.orderprocessingsystem.product.query.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 *    reviewCount, searchKeywords — all pre-computed by the event
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
    private String category;

    /** When the product was originally created (from the event). */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** When the read model was last updated by an event. */
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;
}
```

---

### 10.2 Query Repository — ProductQueryRepository

The query repository is optimized for reads — it contains the find methods, sorting, filtering, and pagination that the API consumers need.

```java
package org.viators.orderprocessingsystem.product.query.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;

import java.util.List;
import java.util.Optional;

/**
 * Repository for read operations on the Product query model.
 *
 * Contrast this with ProductCommandRepository — the command repo has
 * findByUuid and existsByName (for write support). This repo has
 * findAll, findByCategory, and other query-oriented methods.
 *
 * In a real application, this repository would likely extend
 * JpaSpecificationExecutor for dynamic filtering, or use @Query
 * methods with projections for specific use cases (e.g., returning
 * only name and price for autocomplete dropdowns).
 *
 * All methods here are implicitly read-only at the transaction level
 * (enforced by the query handler's @Transactional(readOnly = true)).
 */
@Repository
public interface ProductQueryRepository extends JpaRepository<ProductReadEntity, Long> {

    /**
     * Finds a product read model by its public UUID.
     *
     * @param uuid the product's public identifier
     * @return the read entity if found
     */
    Optional<ProductReadEntity> findByUuid(String uuid);

    /**
     * Finds all products in a specific category.
     *
     * @param category the category to filter by
     * @return list of products in the given category
     */
    List<ProductReadEntity> findByCategory(String category);

    /**
     * Finds all products ordered by creation date (newest first).
     *
     * @return all products sorted by createdAt descending
     */
    List<ProductReadEntity> findAllByOrderByCreatedAtDesc();
}
```

---

### 10.3 Query Handlers — Processing Read Requests

The query handler is intentionally thin — it just fetches data from the repository and returns it. No business logic, no writes, no event publishing. If you find yourself adding business rules to a query handler, that logic probably belongs on the command side.

```java
package org.viators.orderprocessingsystem.product.query.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductDetailResponse;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductSummaryResponse;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;
import org.viators.orderprocessingsystem.product.query.repository.ProductQueryRepository;

import java.util.List;

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

    /**
     * Returns all products as a lightweight summary list.
     *
     * @return list of product summaries
     */
    public List<ProductSummaryResponse> handleFindAll() {
        log.debug("Handling query: find all products");

        return productQueryRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * Returns a single product's full details.
     *
     * @param uuid the product's public identifier
     * @return the product detail response
     * @throws ResourceNotFoundException if no product exists with the given UUID
     */
    public ProductDetailResponse handleFindByUuid(String uuid) {
        log.debug("Handling query: find product by UUID {}", uuid);

        return productQueryRepository.findByUuid(uuid)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "uuid", uuid
                ));
    }

    /**
     * Returns all products in a specific category.
     *
     * @param category the category to filter by
     * @return list of product summaries in the category
     */
    public List<ProductSummaryResponse> handleFindByCategory(String category) {
        log.debug("Handling query: find products by category {}", category);

        return productQueryRepository.findByCategory(category)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    // ── Private mapping methods ─────────────────────────────────────

    /**
     * Maps a read entity to a summary response.
     * Summary = just the fields needed for a product list view.
     */
    private ProductSummaryResponse toSummaryResponse(ProductReadEntity entity) {
        return new ProductSummaryResponse(
                entity.getUuid(),
                entity.getName(),
                entity.getPrice(),
                entity.getCategory()
        );
    }

    /**
     * Maps a read entity to a detail response.
     * Detail = all fields including description and timestamps.
     */
    private ProductDetailResponse toDetailResponse(ProductReadEntity entity) {
        return new ProductDetailResponse(
                entity.getUuid(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getCategory(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt()
        );
    }
}
```

---

### 10.4 Query DTOs — Response Shaping

The query side can return multiple response shapes from the same entity. A list view doesn't need all fields — just name, price, and category. A detail view needs everything.

```java
package org.viators.orderprocessingsystem.product.query.dto.response;

import java.math.BigDecimal;

/**
 * Lightweight product response for list views.
 *
 * Contains only the fields a client needs to render a product card
 * or list item. Keeping responses lean reduces payload size and
 * improves API response times — especially important for mobile clients.
 *
 * @param uuid     the product's public identifier
 * @param name     the product display name
 * @param price    the product price
 * @param category the product category
 */
public record ProductSummaryResponse(
        String uuid,
        String name,
        BigDecimal price,
        String category
) {}
```

```java
package org.viators.orderprocessingsystem.product.query.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full product response for detail views.
 *
 * Contains all fields a client needs to render a product detail page.
 * Note: this includes timestamps from the read model, not the write
 * model. In an eventually consistent system, these may differ slightly
 * from the write model's timestamps due to event processing delay.
 *
 * @param uuid           the product's public identifier
 * @param name           the product display name
 * @param description    the product description
 * @param price          the product price
 * @param category       the product category
 * @param createdAt      when the product was originally created
 * @param lastModifiedAt when the read model was last updated
 */
public record ProductDetailResponse(
        String uuid,
        String name,
        String description,
        BigDecimal price,
        String category,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {}
```

---

### 10.5 Query Controller — REST Endpoints for Reads

```java
package org.viators.orderprocessingsystem.product.query.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductDetailResponse;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductSummaryResponse;
import org.viators.orderprocessingsystem.product.query.handler.ProductQueryHandler;

import java.util.List;

/**
 * REST controller for product read operations (Query side).
 *
 * Same base path as the Command controller (/api/v1/products).
 * Spring can distinguish them because they handle different HTTP methods:
 * - Command controller: POST, PATCH (writes)
 * - Query controller: GET (reads)
 *
 * This means the CQRS split is invisible to API consumers. They see
 * a standard REST resource that supports GET, POST, and PATCH.
 * The internal separation is an implementation detail.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductQueryController {

    private final ProductQueryHandler productQueryHandler;

    /**
     * Lists all products or filters by category.
     *
     * GET /api/v1/products           -> all products
     * GET /api/v1/products?category=Electronics -> filtered by category
     *
     * @param category optional category filter
     * @return list of product summaries
     */
    @GetMapping
    public ResponseEntity<List<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) String category
    ) {
        List<ProductSummaryResponse> products = (category != null && !category.isBlank())
                ? productQueryHandler.handleFindByCategory(category)
                : productQueryHandler.handleFindAll();

        return ResponseEntity.ok(products);
    }

    /**
     * Gets a single product's full details.
     *
     * GET /api/v1/products/{uuid}
     *
     * @param uuid the product's public identifier
     * @return the product detail response
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ProductDetailResponse> getProductByUuid(
            @PathVariable String uuid
    ) {
        return ResponseEntity.ok(productQueryHandler.handleFindByUuid(uuid));
    }
}
```

---

## 11. Exception Handling for CQRS

The exception classes used in this guide (`DuplicateResourceException`, `ResourceNotFoundException`) are your existing custom exceptions handled by your `GlobalExceptionHandler`. No CQRS-specific exception handling is needed — the pattern is about architecture, not error handling.

However, one CQRS-specific scenario to be aware of: **optimistic locking failures**.

```java
package org.viators.orderprocessingsystem.exceptions;

/**
 * Add this handler method to your existing GlobalExceptionHandler.
 *
 * OptimisticLockException occurs when two users try to update the
 * same product simultaneously. The first write succeeds, the second
 * finds that the version has changed and fails. This is the correct
 * behavior — it prevents silent data loss from last-write-wins.
 *
 * The client should retry the operation (re-read, re-apply, re-submit).
 * HTTP 409 Conflict is the standard status code for this scenario.
 */

// Add to GlobalExceptionHandler:

// @ExceptionHandler(OptimisticLockException.class)
// public ResponseEntity<ErrorResponse> handleOptimisticLock(
//         OptimisticLockException ex,
//         HttpServletRequest request
// ) {
//     log.warn("Optimistic lock conflict: {}", ex.getMessage());
//     var error = ErrorResponse.builder()
//             .status(HttpStatus.CONFLICT.value())
//             .errorCode("CONCURRENT_MODIFICATION")
//             .message("This resource was modified by another user. Please refresh and try again.")
//             .path(request.getRequestURI())
//             .timestamp(LocalDateTime.now())
//             .build();
//     return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
// }
```

---

## 12. How Everything Connects: Request Lifecycle

### Command Flow: Creating a Product

```
Client
  │
  │  POST /api/v1/products
  │  { "name": "Keyboard", "price": 79.99, "category": "Electronics" }
  │
  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ProductCommandController                          │
│                                                                     │
│  1. @Valid deserializes and validates CreateProductRequest           │
│  2. Maps DTO to CreateProductCommand                                │
│  3. Calls productCommandHandler.handle(command)                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ProductCommandHandler                             │
│                                                                     │
│  4. Checks existsByName("Keyboard") → false                        │
│  5. Builds ProductWriteEntity with UUID, status=ACTIVE              │
│  6. Saves to "product" table (write model)                          │
│  7. Publishes ProductCreatedEvent via ApplicationEventPublisher     │
│  8. Returns UUID to controller                                      │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         HTTP Response                                │
│  201 Created                                                        │
│  Location: /api/v1/products/a1b2c3d4-...                           │
│  { "uuid": "a1b2c3d4-...", "message": "Product created" }          │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
              (Transaction commits, event is dispatched)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              ProductEventListener (async, separate thread)           │
│                                                                     │
│  9. Receives ProductCreatedEvent                                    │
│  10. Builds ProductReadEntity from event data                       │
│  11. Saves to "product_read_view" table (read model)                │
└─────────────────────────────────────────────────────────────────────┘
```

### Query Flow: Listing Products

```
Client
  │
  │  GET /api/v1/products
  │
  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ProductQueryController                            │
│                                                                     │
│  1. No category param → calls handleFindAll()                       │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ProductQueryHandler                               │
│                                                                     │
│  2. Queries "product_read_view" table (read model)                  │
│  3. Maps entities to ProductSummaryResponse DTOs                    │
│  4. Returns list                                                    │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         HTTP Response                                │
│  200 OK                                                             │
│  [ { "uuid": "...", "name": "Keyboard", "price": 79.99, ... } ]    │
└─────────────────────────────────────────────────────────────────────┘
```

**Key observation**: the query side never touches the "product" (write) table. It reads exclusively from "product_read_view". This is the core of CQRS — complete separation of read and write data paths.

---

## 13. Eventual Consistency: The Trade-Off You Must Understand

CQRS with async event processing introduces **eventual consistency** — there's a brief window after a write where the read model doesn't yet reflect the change.

### What This Means in Practice

```
Timeline:
  T0  ──────────────  T1  ──────────────  T2
  │                   │                    │
  Client creates      Event listener       Read model
  product (write      processes event      is now
  model updated,      (async, separate     up to date
  response sent)      thread)
  │                   │                    │
  │ ← Consistency gap → │                  │
  │                                        │
  If client reads HERE, they might         Here, the data
  not see their own write yet.             is consistent.
```

### How Long is the Gap?

With in-process Spring events (this guide), the gap is typically **milliseconds** — the event is dispatched immediately after the transaction commits, and the async listener processes it on the thread pool. In practice, a client would need to fire a GET request within milliseconds of the POST response to observe the inconsistency.

With a message broker (Kafka/RabbitMQ), the gap can be **seconds to minutes** depending on consumer lag, network latency, and processing time.

### Strategies for Handling Eventual Consistency

**1. Accept it (most common)**:
For product catalogs, dashboards, and list views, a few milliseconds of staleness is invisible to users. This is the approach we take in this guide.

**2. Return the write result directly**:
The POST response includes the UUID and key fields. The client can display "Product created: Keyboard" immediately without needing to read from the query side.

**3. Read-your-writes consistency**:
After a write, bypass the query side and read directly from the write model for that specific user's next request. This adds complexity but guarantees the writing user sees their own changes immediately.

**4. Client-side optimistic update**:
The frontend optimistically adds the new item to the list before the server confirms the read model is updated. React, Angular, and other frameworks support this pattern natively.

---

## 14. Testing CQRS Components

CQRS components are highly testable because of their clear separation of concerns. Each component has a single responsibility and well-defined inputs/outputs.

### Testing the Command Handler

```java
package org.viators.orderprocessingsystem.product.command.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.viators.orderprocessingsystem.exceptions.DuplicateResourceException;
import org.viators.orderprocessingsystem.product.command.entity.ProductWriteEntity;
import org.viators.orderprocessingsystem.product.command.event.ProductCreatedEvent;
import org.viators.orderprocessingsystem.product.command.model.CreateProductCommand;
import org.viators.orderprocessingsystem.product.command.repository.ProductCommandRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductCommandHandler.
 *
 * These tests verify the command handler's behavior in isolation.
 * All dependencies are mocked — we're testing the handler's logic,
 * not the database or Spring's event system.
 *
 * Structure: Each command type gets a @Nested class. Each business
 * rule and edge case gets its own @Test method with a descriptive name.
 */
@ExtendWith(MockitoExtension.class)
class ProductCommandHandlerTest {

    @Mock
    private ProductCommandRepository productCommandRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandHandler commandHandler;

    @Nested
    @DisplayName("CreateProductCommand")
    class CreateProductTests {

        @Test
        @DisplayName("should create product and publish event when name is unique")
        void shouldCreateProductAndPublishEvent() {
            // ── Arrange ─────────────────────────────────────────────
            var command = new CreateProductCommand(
                    "Keyboard", "Mechanical keyboard", new BigDecimal("79.99"), "Electronics"
            );

            // existsByName returns false → name is available
            when(productCommandRepository.existsByName("Keyboard")).thenReturn(false);

            // save() returns the entity with generated fields populated
            when(productCommandRepository.save(any(ProductWriteEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // ── Act ─────────────────────────────────────────────────
            String uuid = commandHandler.handle(command);

            // ── Assert ──────────────────────────────────────────────
            // Verify a UUID was generated and returned
            assertThat(uuid).isNotNull().isNotBlank();

            // Verify the entity was saved
            verify(productCommandRepository).save(any(ProductWriteEntity.class));

            // Verify the correct event was published
            // ArgumentCaptor lets us inspect the event that was published
            ArgumentCaptor<ProductCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(ProductCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            ProductCreatedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.name()).isEqualTo("Keyboard");
            assertThat(publishedEvent.price()).isEqualByComparingTo(new BigDecimal("79.99"));
            assertThat(publishedEvent.category()).isEqualTo("Electronics");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when name already exists")
        void shouldThrowWhenNameExists() {
            // ── Arrange ─────────────────────────────────────────────
            var command = new CreateProductCommand(
                    "Keyboard", "Mechanical keyboard", new BigDecimal("79.99"), "Electronics"
            );
            when(productCommandRepository.existsByName("Keyboard")).thenReturn(true);

            // ── Act & Assert ────────────────────────────────────────
            assertThatThrownBy(() -> commandHandler.handle(command))
                    .isInstanceOf(DuplicateResourceException.class);

            // Verify no save and no event when validation fails
            verify(productCommandRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
```

### Testing the Query Handler

```java
package org.viators.orderprocessingsystem.product.query.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductDetailResponse;
import org.viators.orderprocessingsystem.product.query.dto.response.ProductSummaryResponse;
import org.viators.orderprocessingsystem.product.query.entity.ProductReadEntity;
import org.viators.orderprocessingsystem.product.query.repository.ProductQueryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductQueryHandler.
 *
 * Query handlers are simple to test — they fetch data and map it.
 * No events, no side effects, no complex branching. If these tests
 * are getting complicated, the handler is doing too much.
 */
@ExtendWith(MockitoExtension.class)
class ProductQueryHandlerTest {

    @Mock
    private ProductQueryRepository productQueryRepository;

    @InjectMocks
    private ProductQueryHandler queryHandler;

    @Test
    @DisplayName("should return all products as summary responses")
    void shouldReturnAllProducts() {
        // ── Arrange ─────────────────────────────────────────────────
        var entity = ProductReadEntity.builder()
                .uuid("test-uuid")
                .name("Keyboard")
                .price(new BigDecimal("79.99"))
                .category("Electronics")
                .createdAt(LocalDateTime.now())
                .build();

        when(productQueryRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(entity));

        // ── Act ─────────────────────────────────────────────────────
        List<ProductSummaryResponse> results = queryHandler.handleFindAll();

        // ── Assert ──────────────────────────────────────────────────
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().name()).isEqualTo("Keyboard");
        assertThat(results.getFirst().uuid()).isEqualTo("test-uuid");
    }

    @Test
    @DisplayName("should return product details when UUID exists")
    void shouldReturnProductDetailsByUuid() {
        // ── Arrange ─────────────────────────────────────────────────
        var entity = ProductReadEntity.builder()
                .uuid("test-uuid")
                .name("Keyboard")
                .description("Mechanical keyboard")
                .price(new BigDecimal("79.99"))
                .category("Electronics")
                .createdAt(LocalDateTime.now())
                .lastModifiedAt(LocalDateTime.now())
                .build();

        when(productQueryRepository.findByUuid("test-uuid"))
                .thenReturn(Optional.of(entity));

        // ── Act ─────────────────────────────────────────────────────
        ProductDetailResponse result = queryHandler.handleFindByUuid("test-uuid");

        // ── Assert ──────────────────────────────────────────────────
        assertThat(result.uuid()).isEqualTo("test-uuid");
        assertThat(result.description()).isEqualTo("Mechanical keyboard");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when UUID doesn't exist")
    void shouldThrowWhenUuidNotFound() {
        when(productQueryRepository.findByUuid("nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryHandler.handleFindByUuid("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

---

## 15. Testing with cURL

### Create a Product
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "Cherry MX Blue switches, RGB backlit",
    "price": 79.99,
    "category": "Electronics"
  }'
```

Expected response (201 Created):
```json
{
  "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Product created successfully"
}
```

### List All Products (Query Side)
```bash
curl -X GET http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <your-jwt-token>"
```

Expected response (200 OK):
```json
[
  {
    "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Mechanical Keyboard",
    "price": 79.99,
    "category": "Electronics"
  }
]
```

### Get Product Details
```bash
curl -X GET http://localhost:8080/api/v1/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Authorization: Bearer <your-jwt-token>"
```

Expected response (200 OK):
```json
{
  "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "Mechanical Keyboard",
  "description": "Cherry MX Blue switches, RGB backlit",
  "price": 79.99,
  "category": "Electronics",
  "createdAt": "2026-03-20T10:30:00",
  "lastModifiedAt": null
}
```

### Update Product Price
```bash
curl -X PATCH http://localhost:8080/api/v1/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890/price \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "newPrice": 89.99
  }'
```

Expected response (200 OK):
```json
{
  "message": "Product price updated successfully"
}
```

### Filter Products by Category
```bash
curl -X GET "http://localhost:8080/api/v1/products?category=Electronics" \
  -H "Authorization: Bearer <your-jwt-token>"
```

### Test Duplicate Name (Should Fail)
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "A duplicate",
    "price": 49.99,
    "category": "Electronics"
  }'
```

Expected response (409 Conflict):
```json
{
  "status": 409,
  "errorCode": "DUPLICATE_RESOURCE",
  "message": "Product with name 'Mechanical Keyboard' already exists",
  "path": "/api/v1/products",
  "timestamp": "2026-03-20T10:35:00"
}
```

---

## 16. CQRS Best Practices Checklist

| Practice | Status | Notes |
|----------|--------|-------|
| Separate write and read models | ✅ | `ProductWriteEntity` and `ProductReadEntity` |
| Separate write and read repositories | ✅ | `ProductCommandRepository` and `ProductQueryRepository` |
| Commands as explicit intentions | ✅ | `CreateProductCommand`, `UpdateProductPriceCommand` |
| Events as past-tense facts | ✅ | `ProductCreatedEvent`, `ProductPriceUpdatedEvent` |
| Events are self-contained | ✅ | Query side never calls back to command side |
| Async event processing | ✅ | `@Async` with custom thread pool |
| Events fire only after commit | ✅ | `@TransactionalEventListener(AFTER_COMMIT)` |
| Read-only transactions on query side | ✅ | `@Transactional(readOnly = true)` |
| Optimistic locking on write model | ✅ | `@Version` on `ProductWriteEntity` |
| No business logic in query handlers | ✅ | Only fetch and map |
| DTOs separate from entities | ✅ | Request DTOs, response DTOs, never entities |
| Bean validation on request DTOs | ✅ | `@NotBlank`, `@Positive`, `@Size` |

---

## 17. Common Mistakes to Avoid

**1. Querying the write model for reads**
The whole point of CQRS is separation. If your query controller calls `ProductCommandRepository.findAll()`, you've bypassed the pattern entirely. Reads go through the query side, always.

**2. Putting business logic in event listeners**
The event listener's job is to update the read model — that's it. If you find yourself adding validation, calculations, or conditional branching in the listener, that logic belongs in the command handler. The listener is a projector, not a service.

**3. Coupling the read model schema to the write model**
The read model should be shaped by what the API consumers need, not by what the write model looks like. If your `ProductReadEntity` is a carbon copy of `ProductWriteEntity`, you're missing the opportunity to optimize the read side.

**4. Synchronous event processing without realizing it**
If you forget `@Async`, the event listener runs synchronously on the command thread. A failure in the read model update would propagate back to the command side, potentially failing the write. Always use `@Async` with `@TransactionalEventListener(AFTER_COMMIT)`.

**5. Forgetting `Propagation.REQUIRES_NEW` on the listener**
After the command transaction commits (`AFTER_COMMIT`), there's no active transaction for the listener to join. Without `REQUIRES_NEW`, the listener's JPA operations will fail silently or throw `TransactionRequiredException`.

**6. Making commands too generic**
`UpdateProductCommand` with 10 optional fields is just CRUD with extra steps. Use specific commands: `UpdateProductPriceCommand`, `RenameProductCommand`, `DeactivateProductCommand`. Each one tells you exactly what's happening and what event it produces.

**7. Not handling event listener failures**
What happens if the listener throws an exception? With `@Async`, the exception is swallowed (logged by Spring's default handler). In production, you need a strategy: dead-letter logging, retry mechanisms, or a rebuild process. For this guide, logging is sufficient. For production, consider Spring Retry (`@Retryable`) on the listener methods.

**8. Assuming immediate consistency**
After a POST that creates a product, a GET on the same millisecond might not return the new product (the async listener hasn't processed the event yet). Design your client to handle this — see section 13 for strategies.

---

## 18. What Comes Next: Event Sourcing and Messaging

This guide covered **pure CQRS**: separate read/write models within a single application using Spring's in-process events. Here's what comes next in your learning journey:

**Event Sourcing** (separate guide): Instead of storing current state in the write model, you store every event that ever happened. The current state is derived by replaying events. This gives you a complete audit trail and the ability to rebuild any projection from scratch. CQRS + Event Sourcing is the most powerful combination — but also the most complex.

**Message Brokers (Kafka/RabbitMQ)** (separate guide): Replace `ApplicationEventPublisher` with a message broker. This enables the command and query sides to run as separate services, on separate machines, scaling independently. Events become durable messages that survive service restarts.

**SAGA Pattern** (separate guide): When a business operation spans multiple services (e.g., "create order → reserve inventory → charge payment"), you need a coordination pattern. SAGA orchestrates or choreographs these multi-step operations with compensating actions for failures.

Each builds on the CQRS foundation you've established here. The command/query separation, the event-driven communication, and the eventual consistency model all carry forward.

---

## Project File Structure After Implementation

```
src/main/java/org/viators/orderprocessingsystem/
├── OrderProcessingSystemApplication.java
├── config/
│   ├── AsyncConfig.java                         ← NEW (CQRS event thread pool)
│   ├── JpaAuditingConfig.java
│   ├── JwtProperties.java
│   └── SecurityConfig.java
├── common/
│   ├── BaseEntity.java
│   └── enums/
│       ├── CategoryEnum.java
│       ├── StatusEnum.java
│       └── UserRolesEnum.java
├── exceptions/
│   ├── DuplicateResourceException.java
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java              ← MODIFIED (add OptimisticLock handler)
├── product/
│   ├── command/                                  ← NEW PACKAGE (Write Side)
│   │   ├── controller/
│   │   │   └── ProductCommandController.java
│   │   ├── dto/
│   │   │   └── request/
│   │   │       ├── CreateProductRequest.java
│   │   │       └── UpdateProductPriceRequest.java
│   │   ├── entity/
│   │   │   └── ProductWriteEntity.java
│   │   ├── event/
│   │   │   ├── ProductCreatedEvent.java
│   │   │   └── ProductPriceUpdatedEvent.java
│   │   ├── handler/
│   │   │   └── ProductCommandHandler.java
│   │   ├── model/
│   │   │   ├── CreateProductCommand.java
│   │   │   └── UpdateProductPriceCommand.java
│   │   └── repository/
│   │       └── ProductCommandRepository.java
│   └── query/                                    ← NEW PACKAGE (Read Side)
│       ├── controller/
│       │   └── ProductQueryController.java
│       ├── dto/
│       │   └── response/
│       │       ├── ProductDetailResponse.java
│       │       └── ProductSummaryResponse.java
│       ├── entity/
│       │   └── ProductReadEntity.java
│       ├── handler/
│       │   └── ProductQueryHandler.java
│       ├── listener/
│       │   └── ProductEventListener.java
│       └── repository/
│           └── ProductQueryRepository.java
├── auth/
│   └── ... (existing)
└── user/
    └── ... (existing)
```
