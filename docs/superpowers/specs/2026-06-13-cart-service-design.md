# Cart Service — Design Specification

**Status:** Approved  
**Date:** 2026-06-13  
**Ticket:** TICKET #2 — Cart Service: In-Memory Shopping Cart  
**Branch:** `feature/cart-service`

## 1. Overview

Build a Cart Service that manages temporary shopping carts for users using in-memory storage, with an interface-based design ready for Redis migration in Phase 3.

## 2. Module Setup

New Maven module `cart-service` under the parent `ecommerce-microservices`.

**Key dependencies** (matching existing project patterns):
- `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-webflux` (for WebClient inter-service calls)
- `spring-cloud-starter-circuitbreaker-resilience4j` (circuit breaker + retry for product lookup)
- `shared-library` (for `ErrorResponse` DTO)
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test` (test scope)

**Port:** `8083`

## 3. Package Structure

```
com.ecommerce.cart
├── CartServiceApplication.java         (@SpringBootApplication, @EnableDiscoveryClient)
├── config/
│   └── CartConfig.java                 (WebClient.Builder @LoadBalanced bean, scheduling)
├── model/
│   ├── Cart.java                       (POJO: userId, items map, totalPrice, timestamps)
│   └── CartItem.java                   (POJO: productId, quantity, price)
├── repository/
│   ├── CartRepository.java             (interface: findByUserId, save, deleteByUserId, deleteAll)
│   └── InMemoryCartRepository.java     (ConcurrentHashMap impl, per-cart locking, scheduled cleanup)
├── service/
│   └── CartService.java                (business logic: add/remove/update/checkout/expiry)
├── client/
│   └── ProductServiceClient.java       (WebClient + @CircuitBreaker + @Retry for product lookup)
├── controller/
│   └── CartController.java             (REST endpoints under /carts)
├── dto/
│   ├── AddItemRequest.java             (productId, quantity)
│   ├── UpdateItemRequest.java          (quantity)
│   ├── CartResponse.java               (response DTO for cart)
│   └── CartItemResponse.java           (response DTO for item)
└── exception/
    └── GlobalExceptionHandler.java     (@RestControllerAdvice using shared ErrorResponse)
```

## 4. Data Model

### CartItem (POJO)
```
productId: String    — Long from Product, stored as String for flexibility
quantity: int        — must be > 0
price: BigDecimal    — captured at add time from Product Service
equals/hashCode on productId
```

### Cart (POJO)
```
userId: String
items: Map<String, CartItem>   — keyed by productId (idempotent: add same product = increment)
totalPrice: BigDecimal          — recomputed on each mutation
createdAt: Instant
lastModifiedAt: Instant         — updated on each mutation
```

**Design decisions:**
- `items` is a `Map` keyed by `productId` — adding the same product twice increments the quantity
- `price` is locked in at add time (product price changes don't affect existing cart items)
- `totalPrice` is stored on `Cart` for O(1) reads, recalculated on writes
- No JPA annotations — purely in-memory POJOs

## 5. Repository Layer

### CartRepository (Interface)
```java
interface CartRepository {
    Optional<Cart> findByUserId(String userId);
    Cart save(Cart cart);
    void deleteByUserId(String userId);
    void deleteAll();
}
```
Abstraction for future Redis implementation. Any Redis impl needs only these 4 methods.

### InMemoryCartRepository
- Backed by `ConcurrentHashMap<String, Cart>` (userId → Cart)
- Per-cart `synchronized` block on the cart's key string for concurrent access isolation
  - Different carts can be modified in parallel
  - Same cart operations are serialized (correct for cart semantics)
- Lazy eviction checked on every access (`lastModifiedAt > 30 days → remove`)
- Scheduled cleanup via `@Scheduled(cron = "0 0 */6 * * *")` — scans every 6 hours and removes expired carts

## 6. Service Layer

### CartService — Business Logic

| Operation | Behavior |
|-----------|----------|
| `getOrCreateCart(userId)` | Returns existing cart or creates new one with `createdAt = now` |
| `addItem(userId, productId, quantity)` | Validates quantity > 0. Looks up product via `ProductServiceClient`. If product exists in cart, increments quantity. Otherwise creates new CartItem. Recalculates total. |
| `updateItemQuantity(userId, productId, quantity)` | If quantity ≤ 0, removes item. Otherwise updates and recalculates. Throws if item not found. |
| `removeItem(userId, productId)` | Removes item from map. Recalculates. Throws if item not found. |
| `getCart(userId)` | Returns cart or 404. Lazy eviction check. |
| `clearCart(userId)` | Deletes entire cart from repository. |
| `checkout(userId)` | Validates cart is not empty. Returns cart snapshot for order processing. Does NOT clear cart (Order Service handles confirmation). |

**Concurrency:** All mutations go through `getCartForModification(userId)` which synchronizes on the userId string intern. This ensures:
- Two parallel `addItem` calls for the same user are serialized (no lost updates)
- Different users' carts are processed in parallel (no global lock)

## 7. Product Service Client

Pattern mirrors `user-service`'s `ProductServiceClient`:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {
    private final WebClient.Builder webClientBuilder;

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public Optional<ProductResponse> getProduct(Long productId) {
        // GET http://product-service/api/products/{id}
        // block with 2s timeout
    }

    private Optional<ProductResponse> getProductFallback(Long productId, Throwable t) {
        log.warn("Fallback for product {}: {}", productId, t.getMessage());
        return Optional.empty();
    }
}
```

Resilience4j config in `application.yml` (same settings as user-service):
- Sliding window: 5, failure threshold: 50%, wait: 5s
- Retry: 3 attempts, 500ms base, exponential backoff ×2

**Degradation behavior:** If product lookup fails → `addItem` returns a 503 with a meaningful message. The cart must have a valid product with a known price.

## 8. REST Controller

`@RestController @RequestMapping("/api/carts")`

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| POST | `/users/{userId}` | — | `CartResponse` | 200 (existing) / 201 (created) |
| POST | `/users/{userId}/items` | `AddItemRequest` | `CartResponse` | 200 |
| PUT | `/users/{userId}/items/{productId}` | `UpdateItemRequest` | `CartResponse` | 200 |
| DELETE | `/users/{userId}/items/{productId}` | — | `CartResponse` | 200 |
| GET | `/users/{userId}` | — | `CartResponse` | 200 |
| DELETE | `/users/{userId}` | — | — | 204 |
| POST | `/users/{userId}/checkout` | — | `CartResponse` | 200 |

**DTOs:**
- `AddItemRequest`: `productId` (Long), `quantity` (int, min 1)
- `UpdateItemRequest`: `quantity` (int, min 1)
- `CartResponse`: `userId`, `items` (List of `CartItemResponse`), `totalPrice`, `createdAt`, `lastModifiedAt`
- `CartItemResponse`: `productId`, `quantity`, `price`, `subtotal` (price × quantity)

## 9. Exception Handling

Using `@RestControllerAdvice` + `ErrorResponse` from shared-library:

- `IllegalArgumentException` → 400 (invalid quantity, empty cart checkout)
- `CartNotFoundException` → 404
- `CartItemNotFoundException` → 404
- `ProductLookupException` → 503
- `Exception` → 500

## 10. Application Configuration

```yaml
spring:
  application:
    name: cart-service

server:
  port: 8083

eureka:
  instance:
    hostname: ${INSTANCE_HOSTNAME:localhost}
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true

resilience4j:
  circuitbreaker:
    instances:
      productService:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
  retry:
    instances:
      productService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2

management:
  endpoints:
    web:
      exposure:
        include: health
```

## 11. Cart Expiration

- **TTL:** 30 days from `lastModifiedAt`
- **Eviction strategy:** Lazy check on every access + scheduled cleanup every 6 hours
- **On eviction:** Cart is simply removed from the map. No event published (Order Service handles events).
- **Redis migration note:** Redis `TTL` on the cart key will replace both lazy + scheduled eviction

## 12. Testing Strategy

### Unit Tests (`CartServiceTest`)
- Add item to cart, verify item and total
- Add same product twice, verify quantity incremented
- Remove item, verify removed and total updated
- Update quantity, verify changed and total recalculated
- Clear cart, verify empty
- Checkout with empty cart → throws
- Checkout with items → returns cart snapshot
- 30-day expiry → cart not returned after expiry
- Concurrent adds via `ExecutorService` + `CountDownLatch` — 10 threads adding same product → final quantity = 10

### Integration Tests (`CartControllerTest`)
- `@WebMvcTest(CartController.class)` with mocked `CartService`
- Test all 7 endpoints: status codes, response bodies, error cases
- Test 404 for non-existent cart/user
- Test 400 for invalid quantities

## 13. Gateway & Docker Updates

- **API Gateway:** Add route `cart-service` → `lb://cart-service` with predicate `/api/carts/**`
- **docker-compose.yml:** Add `cart-service` service (port 8083, same pattern as other services)
- **Dockerfile:** Multi-stage build following existing pattern
- **Root pom.xml:** Add `<module>cart-service</module>`

## 14. Non-Goals (Phase 2 Scope)

- No event publishing (deferred to Order Service in a later ticket)
- No Redis integration (interface is ready, impl deferred to Phase 3)
- No authentication/authorization on cart endpoints (will be added later)
- No persistent storage (in-memory only, data lost on restart)
