# Inventory Service — Design Spec

## Overview

Inventory Service manages product stock levels and handles the reservation/confirmation/cancellation lifecycle for orders. It ensures no overselling occurs through optimistic locking with retry logic, and coordinates with the order service via events.

## Architecture

The service follows the same patterns as existing services (multi-module Maven, JPA, Eureka client, Spring Cloud Stream for events). It introduces two JPA entities—`Inventory` (stock levels per product) and `InventoryReservation` (individual reservation lifecycle records)—to cleanly separate stock tracking from reservation state management.

## Tech Stack

- **Spring Boot 3.4.4** with Java 21
- **Spring Data JPA** with H2 (file-based) for persistence
- **Spring Cloud Stream** + RabbitMQ for event publishing/consuming
- **Eureka Client** for service discovery
- **SpringDoc OpenAPI** for API docs
- **Lombok** for boilerplate reduction
- **H2** database (file-based, matches product-service pattern)

## Module & Shared Library Changes

### New Module: `inventory-service`

- Parent POM: add `<module>inventory-service</module>` to `<modules>`
- `inventory-service/pom.xml`: depends on shared-library, spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-actuator, spring-cloud-starter-netflix-eureka-client, spring-cloud-stream, spring-cloud-stream-binder-rabbit, springdoc-openapi-starter-webmvc-ui, h2, lombok
- Port: 8084
- Package: `com.ecommerce.inventory`

### Shared Library Additions

**New event classes** (in `com.ecommerce.shared.event`):
- `InventoryConfirmedEvent` — fields: productId, reservationId, orderId
- `InventoryReservationCancelledEvent` — fields: productId, reservationId, orderId

**New event classes:**
- `InventoryConfirmedEvent` — fields: productId, reservationId, orderId
- `InventoryReservationCancelledEvent` — fields: productId, reservationId, orderId
- `OrderCancelledEvent` — fields: orderId, reason (needed for event handler)

**Modified files:**
- `DomainEvent.java` — register all new events in `@JsonSubTypes`
- `EventConstants.java` — add `INVENTORY_CONFIRMED_QUEUE = "inventory.confirmed.queue"` and `INVENTORY_CANCELLED_QUEUE = "inventory.cancelled.queue"`
- `RabbitMQEventPublisher.java` — add `if` branches for the new event types in `resolveBinding()`

## Domain Model

### Inventory Entity

| Field | Type | Notes |
|-------|------|-------|
| id | Long (PK) | Auto-generated |
| productId | String | Unique, not null |
| totalStock | Integer | Total stock available |
| reservedStock | Integer | Currently reserved, default 0 |
| version | Long | `@Version` for optimistic locking |

`availableStock` is calculated as `totalStock - reservedStock` (not persisted).

### InventoryReservation Entity

| Field | Type | Notes |
|-------|------|-------|
| id | Long (PK) | Auto-generated |
| reservationId | String | UUID, unique, used for tracking/linking |
| productId | String | Not null |
| orderId | String | Not null |
| quantity | Integer | Reserved quantity |
| status | ReservationStatus | PENDING, CONFIRMED, CANCELLED |
| createdAt | LocalDateTime | |
| updatedAt | LocalDateTime | |
| version | Long | `@Version` |

### ReservationStatus Enum

- `PENDING` — reserved, awaiting confirmation
- `CONFIRMED` — finalized, stock decremented
- `CANCELLED` — released, stock restored

## Repository Layer

### InventoryRepository

- `Optional<Inventory> findByProductId(String productId)`

### InventoryReservationRepository

- `Optional<InventoryReservation> findByReservationId(String reservationId)`
- `Optional<InventoryReservation> findByProductIdAndOrderIdAndStatus(String productId, String orderId, ReservationStatus status)`
- `List<InventoryReservation> findByOrderId(String orderId)`

## Service Layer

### InventoryService

**`checkStock(productId)`** — Returns `InventoryResponse` with productId, totalStock, reservedStock, availableStock.

**`getAvailableQuantity(productId)`** — Returns just the available quantity.

**`reserveStock(productId, orderId, quantity)`** — Core reservation logic:
1. Find `Inventory` by productId (throw `ProductNotFoundException` if missing)
2. Validate `quantity <= availableStock` (throw `InsufficientStockException` and publish `InventoryReservationFailedEvent` if not)
3. Create `InventoryReservation` (status=PENDING, reservationId=UUID)
4. Increment `reservedStock += quantity`
5. Save both entities within a `@Transactional` block
6. Publish `InventoryReservedEvent` on success
7. Catch `OptimisticLockingFailureException` and retry up to 3 times with exponential backoff (100ms, 200ms, 400ms). After exhaustion, throw `ReservationConflictException`.

**`confirmReservation(reservationId)`** — Finalizes a PENDING reservation:
1. Find `InventoryReservation` by reservationId (throw `ReservationNotFoundException` if missing)
2. Validate status == PENDING (throw `InvalidReservationStatusException`)
3. Set status = CONFIRMED
4. Decrement `totalStock -= quantity` and `reservedStock -= quantity` on the `Inventory` entity
5. Publish `InventoryConfirmedEvent`

**`cancelReservation(reservationId)`** — Compensates a PENDING reservation:
1. Find `InventoryReservation` by reservationId (throw `ReservationNotFoundException` if missing)
2. Validate status == PENDING (throw `InvalidReservationStatusException`)
3. Set status = CANCELLED
4. Decrement `reservedStock -= quantity` on the `Inventory` entity (stock goes back to available)
5. Publish `InventoryReservationCancelledEvent`

### Event Handlers

**`OrderCancelledEventHandler`** extends `EventConsumer<OrderCancelledEvent>`:
- On receiving an order cancellation event, look up reservations by orderId with status=PENDING
- Call `cancelReservation()` for each to release stock

## REST API

Base path: `/api/inventory`

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| GET | `/api/inventory/products/{productId}` | — | `InventoryResponse` | Check stock for a product |
| GET | `/api/inventory/products/{productId}/available` | — | Integer | Get available quantity |
| POST | `/api/inventory/products/{productId}/reserve` | `ReserveRequest` | `ReserveResponse` | Reserve stock for an order |
| POST | `/api/inventory/products/{productId}/confirm` | `ConfirmRequest` | — | Confirm a reservation |
| POST | `/api/inventory/products/{productId}/cancel` | `CancelRequest` | — | Cancel a reservation |

### DTOs

- **InventoryResponse**: productId, totalStock, reservedStock, availableStock
- **ReserveRequest**: orderId (String), quantity (Integer)
- **ReserveResponse**: reservationId (String), productId, quantity, status
- **ConfirmRequest**: reservationId (String)
- **CancelRequest**: reservationId (String)

### HTTP Status Codes

- 200 — Success
- 201 — Reservation created
- 400 — Bad request (invalid input, insufficient stock, invalid status transition)
- 404 — Product or reservation not found
- 409 — Optimistic locking conflict (retry exhaustion)

## Error Handling

Custom exceptions (extending `RuntimeException`):
- `ProductNotFoundException` → 404
- `InsufficientStockException` → 400
- `ReservationNotFoundException` → 404
- `InvalidReservationStatusException` → 400
- `ReservationConflictException` → 409

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps each to `ErrorResponse` from shared-library, consistent with other services.

## Configuration

### application.yml

```yaml
server:
  port: 8084

spring:
  application:
    name: inventory-service
  datasource:
    url: jdbc:h2:file:./data/inventorydb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  cloud:
    stream:
      bindings:
        inventoryReserved-out-0:
          destination: inventory.events
        inventoryReservationFailed-out-0:
          destination: inventory.events
        inventoryConfirmed-out-0:
          destination: inventory.events
        inventoryReservationCancelled-out-0:
          destination: inventory.events
        orderCancelled-in-0:
          destination: order.events
          group: inventory-service

eureka:
  instance:
    hostname: ${INSTANCE_HOSTNAME:localhost}
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true
```

### API Gateway Route

```yaml
- id: inventory-service
  uri: lb://inventory-service
  predicates:
    - Path=/api/inventory/**
```

## Testing

### Unit Tests (InventoryServiceTest)

- `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`
- Test reservation success flow
- Test insufficient stock scenario
- test confirm reservation flow
- Test cancel reservation flow
- Test optimistic locking retry with `Mockito.doThrow(OptimisticLockingFailureException.class)`
- Concurrent reservation test with `ExecutorService` + `CountDownLatch` + `CyclicBarrier` to verify no overselling under race conditions

### Controller Tests (InventoryControllerTest)

- `@WebMvcTest(InventoryController.class)` with MockMvc
- Test all 5 endpoints
- Test validation error responses (invalid input)
- Test not-found scenarios

### Integration Test (InventoryServiceIntegrationTest)

- `@SpringBootTest` with full context
- Create Inventory record, test full reserve → confirm flow
- Test reserve → cancel flow
- Test overselling prevention

## Git Flow

Branch: `feature/inventory-service`

Commit sequence (conventional commits):
1. `feat(inventory): add inventory-service module scaffolding`
2. `feat(inventory): add Inventory and InventoryReservation entities`
3. `feat(inventory): add repositories, DTOs, and exceptions`
4. `feat(inventory): add InventoryService with reservation logic and retry`
5. `feat(inventory): add InventoryController with REST endpoints`
6. `feat(inventory): add event publishing for inventory lifecycle`
7. `feat(inventory): add OrderCancelledEventHandler`
8. `feat(shared): add InventoryConfirmedEvent and InventoryReservationCancelledEvent`
9. `test(inventory): add unit and integration tests`
10. `feat(inventory): register in gateway and docker-compose`
