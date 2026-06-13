# Order Service — Saga Pattern for Distributed Transactions

## Status: Draft

## Goal

Build the Order Service that orchestrates order creation using the Saga pattern (choreography style), coordinating Cart, Inventory, Product, and Payment services through a hybrid REST + event-driven flow.

## Architecture

### Hybrid Choreography Saga

- **OrderSaga class** manages the state machine and step/compensation logic internally
- **REST calls** for the critical path: fetching cart, reserving inventory, processing payment
- **Events** for audit trail, state propagation, and decoupled side-effects (cart clearing, inventory release)
- **Order status** reflects saga progress; compensations triggered on step failure

### Service Layout

| Service | Port | Package | DB |
|---------|------|---------|----|
| order-service | 8085 | `com.ecommerce.order` | H2 file |
| payment-service | 8086 | `com.ecommerce.payment` | H2 in-memory |

### Module Structure

```
order-service/
├── OrderServiceApplication.java
├── config/
│   └── OrderConfig.java                # @LoadBalanced WebClient.Builder bean
├── entity/
│   ├── Order.java                      # JPA entity
│   ├── OrderItem.java                  # JPA entity
│   └── OrderEvent.java                 # JPA audit trail entity
├── entity/enums/
│   └── OrderStatus.java                # PENDING, CART_VALIDATED, INVENTORY_RESERVED, PAYMENT_PENDING, CONFIRMED, FAILED, CANCELLED
├── repository/
│   ├── OrderRepository.java
│   ├── OrderItemRepository.java
│   └── OrderEventRepository.java
├── dto/
│   ├── OrderRequest.java               # userId
│   ├── OrderResponse.java              # full order with items + status
│   └── OrderItemResponse.java
├── controller/
│   └── OrderController.java            # REST endpoints
├── service/
│   ├── OrderService.java               # business logic (create, get, cancel)
│   └── OrderSaga.java                  # saga step/compensation manager
├── client/
│   ├── CartServiceClient.java          # WebClient → cart-service
│   ├── ProductServiceClient.java       # WebClient → product-service
│   ├── InventoryServiceClient.java     # WebClient → inventory-service
│   └── PaymentServiceClient.java       # WebClient → payment-service
├── event/
│   ├── InventoryEventHandlers.java     # Consumes inventory.events
│   ├── PaymentEventHandlers.java       # Consumes payment.events
│   └── OrderEventPublisher.java        # Publishes order events
├── exception/
│   ├── OrderNotFoundException.java
│   ├── InvalidOrderStateException.java
│   ├── SagaStepFailedException.java
│   └── GlobalExceptionHandler.java

payment-service/
├── PaymentServiceApplication.java
├── config/
│   └── PaymentConfig.java
├── controller/
│   └── PaymentController.java          # POST /api/payments/process
├── dto/
│   ├── PaymentRequest.java             # orderId, amount
│   └── PaymentResponse.java            # paymentId, status, success
├── service/
│   └── PaymentService.java             # Simulate: 90% success, 10% failure
├── event/
│   └── PaymentEventPublisher.java      # Publishes PaymentProcessedEvent
├── exception/
│   └── GlobalExceptionHandler.java
```

## Entities

### OrderStatus Enum

```
PENDING → CART_VALIDATED → INVENTORY_RESERVED → PAYMENT_PENDING → CONFIRMED
                                                                   → FAILED
                                                                   → CANCELLED
```

### Order

| Field | Type | Notes |
|-------|------|-------|
| id | Long (auto) | PK |
| orderId | UUID | Unique, used as idempotency key |
| userId | Long | |
| totalPrice | BigDecimal | |
| status | OrderStatus | Enum mapped to string |
| createdAt | LocalDateTime | |
| updatedAt | LocalDateTime | |
| version | Long | @Version for optimistic locking |

### OrderItem

| Field | Type | Notes |
|-------|------|-------|
| id | Long (auto) | PK |
| orderId | Long | FK to Order |
| productId | Long | |
| productName | String | snapshot at order time |
| quantity | int | |
| price | BigDecimal | snapshot at order time |

### OrderEvent

| Field | Type | Notes |
|-------|------|-------|
| id | Long (auto) | PK |
| orderId | String | |
| eventType | String | e.g. "ORDER_CREATED", "INVENTORY_RESERVED" |
| details | String | JSON payload |
| timestamp | LocalDateTime | |

## REST Endpoints

| Method | Path | Action | Request | Response |
|--------|------|--------|---------|----------|
| POST | `/api/orders` | Create order (triggers saga) | `{ "userId": 1 }` | OrderResponse |
| GET | `/api/orders/{orderId}` | Get order details | — | OrderResponse |
| GET | `/api/orders/users/{userId}` | List user's orders | — | List\<OrderResponse\> |
| PUT | `/api/orders/{orderId}/cancel` | Cancel order | — | OrderResponse |

## Saga Flow — Detailed Steps

### Happy Path

```
1. POST /api/orders { userId }
2. OrderSaga.createOrder(userId):
   a. GET /api/carts/users/{userId}/checkout → CartResponse (items, total)
   b. For each item: GET /api/products/{id} → validate exists
   c. Save Order(status=PENDING), OrderItems
   d. Save OrderEvent("ORDER_CREATED")
   e. Publish OrderCreatedEvent
   f. For each item: POST /api/inventory/products/{productId}/reserve { orderId, quantity }
      → ReserveResponse (reservationId, status=PENDING/CONFIRMED)
   g. Update Order status → INVENTORY_RESERVED
   h. Save OrderEvent("INVENTORY_RESERVED")
   i. POST /api/payments/process { orderId, amount } → PaymentResponse
   j. If success:
        Update Order status → CONFIRMED
        Publish OrderConfirmedEvent
        Save OrderEvent("ORDER_CONFIRMED")
      If fail:
        → COMPENSATE: for each item: POST /api/inventory/products/{productId}/cancel { reservationId }
        Update Order status → FAILED
        Publish OrderFailedEvent
        Save OrderEvent("ORDER_FAILED")
```

### Compensation Path

| Failure Step | Compensation |
|---|---|
| Inventory reservation fails | Mark Order FAILED, publish OrderFailedEvent |
| Payment fails | Release inventory reservations (POST /api/inventory/products/{productId}/cancel), mark FAILED, publish OrderFailedEvent |
| Any unexpected error | Mark FAILED, publish OrderFailedEvent |

### Cancellation Path

```
PUT /api/orders/{orderId}/cancel
  → Validate order is in cancellable state (PENDING, INVENTORY_RESERVED, PAYMENT_PENDING)
  → Update Order status → CANCELLED
  → Publish OrderCancelledEvent
  → Save OrderEvent("ORDER_CANCELLED")
  → Inventory Service (existing handler) cancels pending reservations
```

## Event Flow

### Events Published (by order-service)

| Event | Binding | Exchange | Trigger |
|-------|---------|----------|---------|
| OrderCreatedEvent | orderCreated-out-0 | order.events | Order created |
| OrderConfirmedEvent | orderConfirmed-out-0 | order.events | Payment succeeded |
| OrderFailedEvent | orderFailed-out-0 | order.events | Saga step failed |
| OrderCancelledEvent | orderCancelled-out-0 | order.events | User cancelled |

### Events Consumed (by order-service)

| Event | Source Exchange | Handler |
|-------|----------------|---------|
| InventoryReservedEvent | inventory.events | Update order status (async confirmation) |
| InventoryReservationFailedEvent | inventory.events | Mark order FAILED |
| PaymentProcessedEvent | payment.events | Confirm order or trigger compensation |

### Events Consumed (by other services)

| Event | Consumer | Action |
|-------|----------|--------|
| OrderCreatedEvent | cart-service | Clear user's cart |
| OrderCancelledEvent | inventory-service | Release pending reservations (already exists) |

## Service Communication

### REST Clients (with Circuit Breaker + Retry)

Each client uses `@LoadBalanced WebClient.Builder`, `@Retry`, and `@CircuitBreaker` following the existing `cart-service` pattern:

- **CartServiceClient**: GET `/api/carts/users/{userId}/checkout`
- **ProductServiceClient**: GET `/api/products/{id}`
- **InventoryServiceClient**: POST `/api/inventory/products/{productId}/reserve`, POST `/api/inventory/products/{productId}/confirm`, POST `/api/inventory/products/{productId}/cancel`
- **PaymentServiceClient**: POST `/api/payments/process`

### Idempotency

- `orderId` (UUID) used as idempotency key for all REST calls
- If an order already exists with the given UUID, return existing order
- Inventory reservations and payment processing keyed by orderId

## Error Handling

- **OrderNotFoundException** (404) — order not found
- **InvalidOrderStateException** (409) — wrong status for operation
- **SagaStepFailedException** (500) — saga step failed with details
- **GlobalExceptionHandler** — returns shared `ErrorResponse`
- All REST clients fallback gracefully with `Optional.empty()` or throw domain exceptions

## Testing Strategy

### Unit Tests

| Test Class | What it tests |
|------------|---------------|
| OrderServiceTest | createOrder, getOrder, cancelOrder, status transitions |
| OrderSagaTest | saga steps, compensation on failure |
| PaymentServiceTest | simulated payment processing |

### Integration Tests

| Test Class | What it tests |
|------------|---------------|
| OrderControllerTest | REST endpoints with mocked services |
| OrderSagaIntegrationTest | Full saga flow with mocked service responses |
| CompensationTest | Inventory fail → order FAILED; Payment fail → inventory released |

### Test Scenarios

| Scenario | Expected |
|----------|----------|
| Happy path | Order CONFIRMED, events published, cart cleared |
| Inventory reservation fails | Order FAILED, no payment attempted |
| Payment fails | Order FAILED, inventory reservations cancelled |
| Cancel pending order | Order CANCELLED, inventory released |
| Get non-existent order | 404 |
| Cancel already confirmed order | 409 conflict |

## Changes to Existing Services

### Cart Service

Add `OrderCreatedEventHandler` implementing `Consumer<OrderCreatedEvent>`:
- On `OrderCreatedEvent` → call `cartService.clearCart(userId)`
- Register in `application.yml` as `orderCreated-in-0` consuming `order.events`

### API Gateway

Add route: `order-service` → `lb://order-service` with predicate `/api/orders/**`

### Parent POM

Add modules: `order-service`, `payment-service`

### Docker Compose

Add services:
- `order-service` (port 8085, depends on eureka + rabbitmq)
- `payment-service` (port 8086, depends on eureka + rabbitmq)
