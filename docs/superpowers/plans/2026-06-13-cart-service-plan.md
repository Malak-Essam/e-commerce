# Cart Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an in-memory shopping cart microservice with REST APIs, Product Service integration, and circuit breaker resilience.

**Architecture:** Spring Boot service with interface-based `CartRepository` (ConcurrentHashMap impl), per-cart locking for concurrency, WebClient + Resilience4j for Product Service calls, and `@Scheduled` cleanup for 30-day expiry.

**Tech Stack:** Spring Boot 3.4.4, Java 21, WebClient, Resilience4j, Eureka Discovery, JUnit 5, Mockito

---

### Task 1: Cart Service Module Scaffolding

**Files:**
- Create: `cart-service/pom.xml`
- Create: `cart-service/Dockerfile`
- Create: `cart-service/src/main/java/com/ecommerce/cart/CartServiceApplication.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/config/CartConfig.java`
- Create: `cart-service/src/main/resources/application.yml`

- [ ] **Step 1: Create `cart-service/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecommerce</groupId>
        <artifactId>ecommerce-microservices</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>cart-service</artifactId>
    <name>Cart Service</name>
    <description>Microservice for temporary shopping cart management</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ecommerce</groupId>
            <artifactId>shared-library</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `cart-service/src/main/resources/application.yml`**

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

- [ ] **Step 3: Create `CartServiceApplication.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/CartServiceApplication.java`

```java
package com.ecommerce.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `CartConfig.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/config/CartConfig.java`

```java
package com.ecommerce.cart.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CartConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

- [ ] **Step 5: Create `cart-service/Dockerfile`**

File: `cart-service/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY shared-library shared-library
COPY cart-service cart-service
RUN mvn clean package -pl cart-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/cart-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Commit scaffolding**

```bash
git add cart-service/pom.xml cart-service/Dockerfile cart-service/src/
git commit -m "feat(cart): add cart-service module scaffolding"
```

---

### Task 2: Domain Model (Cart + CartItem)

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/model/CartItem.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/model/Cart.java`

- [ ] **Step 1: Create `CartItem.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/model/CartItem.java`

```java
package com.ecommerce.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    private String productId;
    private int quantity;
    private BigDecimal price;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return Objects.equals(productId, cartItem.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(productId);
    }
}
```

- [ ] **Step 2: Create `Cart.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/model/Cart.java`

```java
package com.ecommerce.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    private String userId;
    @Builder.Default
    private Map<String, CartItem> items = new HashMap<>();
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private Instant createdAt;
    private Instant lastModifiedAt;
}
```

- [ ] **Step 3: Commit domain model**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/model/
git commit -m "feat(cart): add Cart and CartItem domain models"
```

---

### Task 3: Repository Interface + In-Memory Implementation

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/repository/CartRepository.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/repository/InMemoryCartRepository.java`

- [ ] **Step 1: Create `CartRepository.java` interface**

File: `cart-service/src/main/java/com/ecommerce/cart/repository/CartRepository.java`

```java
package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;

import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(String userId);

    Cart save(Cart cart);

    void deleteByUserId(String userId);

    void deleteAll();
}
```

- [ ] **Step 2: Create `InMemoryCartRepository.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/repository/InMemoryCartRepository.java`

```java
package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class InMemoryCartRepository implements CartRepository {

    private static final Duration CART_EXPIRATION = Duration.ofDays(30);

    private final Map<String, Cart> carts = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByUserId(String userId) {
        Cart cart = carts.get(userId);
        if (cart != null && isExpired(cart)) {
            carts.remove(userId);
            log.info("Removed expired cart for user: {}", userId);
            return Optional.empty();
        }
        return Optional.ofNullable(cart);
    }

    @Override
    public Cart save(Cart cart) {
        cart.setLastModifiedAt(Instant.now());
        carts.put(cart.getUserId(), cart);
        return cart;
    }

    @Override
    public void deleteByUserId(String userId) {
        carts.remove(userId);
    }

    @Override
    public void deleteAll() {
        carts.clear();
    }

    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanupExpiredCarts() {
        int before = carts.size();
        carts.values().removeIf(this::isExpired);
        int removed = before - carts.size();
        if (removed > 0) {
            log.info("Scheduled cleanup removed {} expired carts", removed);
        }
    }

    private boolean isExpired(Cart cart) {
        return Duration.between(cart.getLastModifiedAt(), Instant.now()).compareTo(CART_EXPIRATION) > 0;
    }
}
```

- [ ] **Step 3: Commit repository layer**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/repository/
git commit -m "feat(cart): add CartRepository interface and InMemoryCartRepository"
```

---

### Task 4: DTOs (Request/Response)

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/dto/ProductResponse.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/dto/AddItemRequest.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/dto/UpdateItemRequest.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/dto/CartResponse.java`

- [ ] **Step 1: Create `ProductResponse.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/dto/ProductResponse.java`

```java
package com.ecommerce.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String category;
}
```

- [ ] **Step 2: Create `AddItemRequest.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/dto/AddItemRequest.java`

```java
package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddItemRequest {

    @NotNull
    private Long productId;

    @Min(1)
    private int quantity;
}
```

- [ ] **Step 3: Create `UpdateItemRequest.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/dto/UpdateItemRequest.java`

```java
package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateItemRequest {

    @Min(1)
    private int quantity;
}
```

- [ ] **Step 4: Create `CartItemResponse.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java`

```java
package com.ecommerce.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private String productId;
    private int quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
```

- [ ] **Step 5: Create `CartResponse.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/dto/CartResponse.java`

```java
package com.ecommerce.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private String userId;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;
    private Instant createdAt;
    private Instant lastModifiedAt;
}
```

- [ ] **Step 6: Commit DTOs**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/dto/
git commit -m "feat(cart): add request/response DTOs"
```

---

### Task 5: Product Service Client (WebClient + Circuit Breaker)

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/client/ProductServiceClient.java`

- [ ] **Step 1: Create `ProductServiceClient.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/client/ProductServiceClient.java`

```java
package com.ecommerce.cart.client;

import com.ecommerce.cart.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public Optional<ProductResponse> getProduct(Long productId) {
        ProductResponse product = webClientBuilder.build()
                .get()
                .uri("http://product-service/api/products/{id}", productId)
                .retrieve()
                .bodyToMono(ProductResponse.class)
                .block(Duration.ofSeconds(2));
        return Optional.ofNullable(product);
    }

    private Optional<ProductResponse> getProductFallback(Long productId, Throwable t) {
        log.warn("Fallback for product {}: {}", productId, t.getMessage());
        return Optional.empty();
    }
}
```

- [ ] **Step 2: Commit client**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/client/
git commit -m "feat(cart): add ProductServiceClient with circuit breaker"
```

---

### Task 6: Cart Service (Business Logic)

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/service/CartService.java`

- [ ] **Step 1: Create `CartService.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/service/CartService.java`

```java
package com.ecommerce.cart.service;

import com.ecommerce.cart.client.ProductServiceClient;
import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;

    public CartResponse getOrCreateCart(String userId) {
        return toCartResponse(cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .createdAt(Instant.now())
                            .lastModifiedAt(Instant.now())
                            .build();
                    return cartRepository.save(newCart);
                }));
    }

    public CartResponse addItem(String userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        ProductResponse product = productServiceClient.getProduct(productId)
                .orElseThrow(() -> new ProductLookupException("Product not found: " + productId));

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        Cart newCart = Cart.builder()
                                .userId(userId)
                                .createdAt(Instant.now())
                                .lastModifiedAt(Instant.now())
                                .build();
                        return cartRepository.save(newCart);
                    });

            String pid = String.valueOf(productId);
            CartItem existingItem = cart.getItems().get(pid);
            if (existingItem != null) {
                existingItem.setQuantity(existingItem.getQuantity() + quantity);
            } else {
                CartItem newItem = CartItem.builder()
                        .productId(pid)
                        .quantity(quantity)
                        .price(product.getPrice())
                        .build();
                cart.getItems().put(pid, newItem);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse updateItemQuantity(String userId, Long productId, int quantity) {
        String pid = String.valueOf(productId);

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            CartItem item = cart.getItems().get(pid);
            if (item == null) {
                throw new CartItemNotFoundException("Item not found: " + productId);
            }

            if (quantity <= 0) {
                cart.getItems().remove(pid);
            } else {
                item.setQuantity(quantity);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse removeItem(String userId, Long productId) {
        String pid = String.valueOf(productId);

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            CartItem removed = cart.getItems().remove(pid);
            if (removed == null) {
                throw new CartItemNotFoundException("Item not found: " + productId);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse getCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));
        return toCartResponse(cart);
    }

    public void clearCart(String userId) {
        cartRepository.deleteByUserId(userId);
    }

    public CartResponse checkout(String userId) {
        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            if (cart.getItems().isEmpty()) {
                throw new IllegalArgumentException("Cannot checkout with an empty cart");
            }

            return toCartResponse(cart);
        }
    }

    private void recalculateTotal(Cart cart) {
        BigDecimal total = cart.getItems().values().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total.setScale(2, RoundingMode.HALF_UP));
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : cart.getItems().values()) {
            itemResponses.add(CartItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build());
        }
        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalPrice(cart.getTotalPrice())
                .createdAt(cart.getCreatedAt())
                .lastModifiedAt(cart.getLastModifiedAt())
                .build();
    }
}
```

- [ ] **Step 2: Commit service**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/service/
git commit -m "feat(cart): add CartService with business logic"
```

---

### Task 7: Exception Classes + Global Handler

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/exception/CartNotFoundException.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/exception/CartItemNotFoundException.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/exception/ProductLookupException.java`
- Create: `cart-service/src/main/java/com/ecommerce/cart/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create exception classes**

File: `cart-service/src/main/java/com/ecommerce/cart/exception/CartNotFoundException.java`

```java
package com.ecommerce.cart.exception;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(String message) {
        super(message);
    }
}
```

File: `cart-service/src/main/java/com/ecommerce/cart/exception/CartItemNotFoundException.java`

```java
package com.ecommerce.cart.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String message) {
        super(message);
    }
}
```

File: `cart-service/src/main/java/com/ecommerce/cart/exception/ProductLookupException.java`

```java
package com.ecommerce.cart.exception;

public class ProductLookupException extends RuntimeException {
    public ProductLookupException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create `GlobalExceptionHandler.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/exception/GlobalExceptionHandler.java`

```java
package com.ecommerce.cart.exception;

import com.ecommerce.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartNotFound(CartNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartItemNotFound(CartItemNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ProductLookupException.class)
    public ResponseEntity<ErrorResponse> handleProductLookup(ProductLookupException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
```

- [ ] **Step 3: Commit exceptions + handler**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/exception/
git commit -m "feat(cart): add custom exceptions and global exception handler"
```

---

### Task 8: Cart Controller (REST Endpoints)

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/controller/CartController.java`

- [ ] **Step 1: Create `CartController.java`**

File: `cart-service/src/main/java/com/ecommerce/cart/controller/CartController.java`

```java
package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateItemRequest;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Shopping cart API")
public class CartController {

    private final CartService cartService;

    @PostMapping("/users/{userId}")
    @Operation(summary = "Get or create cart for user")
    @ApiResponse(responseCode = "200", description = "Existing cart retrieved")
    @ApiResponse(responseCode = "201", description = "New cart created")
    public ResponseEntity<CartResponse> getOrCreateCart(@PathVariable String userId) {
        CartResponse cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PostMapping("/users/{userId}/items")
    @Operation(summary = "Add item to cart")
    @ApiResponse(responseCode = "200", description = "Item added to cart")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable String userId,
            @Valid @RequestBody AddItemRequest request) {
        CartResponse cart = cartService.addItem(userId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @PutMapping("/users/{userId}/items/{productId}")
    @Operation(summary = "Update item quantity in cart")
    @ApiResponse(responseCode = "200", description = "Item quantity updated")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String userId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateItemRequest request) {
        CartResponse cart = cartService.updateItemQuantity(userId, productId, request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/users/{userId}/items/{productId}")
    @Operation(summary = "Remove item from cart")
    @ApiResponse(responseCode = "200", description = "Item removed")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String userId,
            @PathVariable Long productId) {
        CartResponse cart = cartService.removeItem(userId, productId);
        return ResponseEntity.ok(cart);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get cart details")
    @ApiResponse(responseCode = "200", description = "Cart details retrieved")
    public ResponseEntity<CartResponse> getCart(@PathVariable String userId) {
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Clear cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared")
    public ResponseEntity<Void> clearCart(@PathVariable String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/checkout")
    @Operation(summary = "Prepare cart for checkout")
    @ApiResponse(responseCode = "200", description = "Cart ready for checkout")
    public ResponseEntity<CartResponse> checkout(@PathVariable String userId) {
        CartResponse cart = cartService.checkout(userId);
        return ResponseEntity.ok(cart);
    }
}
```

- [ ] **Step 2: Commit controller**

```bash
git add cart-service/src/main/java/com/ecommerce/cart/controller/
git commit -m "feat(cart): add CartController with REST endpoints"
```

---

### Task 9: Unit Tests for CartService

**Files:**
- Create: `cart-service/src/test/java/com/ecommerce/cart/service/CartServiceTest.java`

- [ ] **Step 1: Write the test file**

File: `cart-service/src/test/java/com/ecommerce/cart/service/CartServiceTest.java`

```java
package com.ecommerce.cart.service;

import com.ecommerce.cart.client.ProductServiceClient;
import com.ecommerce.cart.dto.ProductResponse;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productServiceClient);
    }

    @Test
    void addItem_shouldCreateCartAndAddItem() {
        String userId = "user1";
        Long productId = 1L;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(5).category("cat")
                .build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));

        var response = cartService.addItem(userId, productId, 2);

        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals("1", response.getItems().getFirst().getProductId());
        assertEquals(2, response.getItems().getFirst().getQuantity());
        assertEquals(BigDecimal.TEN, response.getItems().getFirst().getPrice());
        assertEquals(new BigDecimal("20.00"), response.getTotalPrice());
    }

    @Test
    void addItem_shouldIncrementQuantityWhenSameProductAddedTwice() {
        String userId = "user1";
        Long productId = 1L;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(5).category("cat")
                .build();

        Cart existingCart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        existingCart.getItems().put("1", CartItem.builder()
                .productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));

        var response = cartService.addItem(userId, productId, 2);

        assertEquals(1, response.getItems().size());
        assertEquals(3, response.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("30.00"), response.getTotalPrice());
    }

    @Test
    void addItem_shouldThrowWhenProductNotFound() {
        String userId = "user1";
        Long productId = 1L;

        when(productServiceClient.getProduct(productId)).thenReturn(Optional.empty());

        assertThrows(ProductLookupException.class, () -> cartService.addItem(userId, productId, 1));
        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    void removeItem_shouldRemoveItemAndRecalculate() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());
        cart.getItems().put("2", CartItem.builder().productId("2").quantity(1).price(new BigDecimal("5.00")).build());
        cart.setTotalPrice(new BigDecimal("25.00"));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.removeItem(userId, 1L);

        assertEquals(1, response.getItems().size());
        assertEquals("2", response.getItems().getFirst().getProductId());
        assertEquals(new BigDecimal("5.00"), response.getTotalPrice());
    }

    @Test
    void removeItem_shouldThrowWhenItemNotFound() {
        String userId = "user1";
        Cart cart = Cart.builder().userId(userId).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(CartItemNotFoundException.class, () -> cartService.removeItem(userId, 1L));
    }

    @Test
    void updateItemQuantity_shouldUpdateAndRecalculate() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.updateItemQuantity(userId, 1L, 5);

        assertEquals(5, response.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("50.00"), response.getTotalPrice());
    }

    @Test
    void updateItemQuantity_shouldRemoveItemWhenQuantityIsZero() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.updateItemQuantity(userId, 1L, 0);

        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getTotalPrice());
    }

    @Test
    void getCart_shouldReturnCart() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        var response = cartService.getCart(userId);

        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void getCart_shouldThrowWhenNotFound() {
        when(cartRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.getCart("unknown"));
    }

    @Test
    void clearCart_shouldDeleteCart() {
        cartService.clearCart("user1");
        verify(cartRepository).deleteByUserId("user1");
    }

    @Test
    void checkout_shouldReturnCartSnapshot() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        var response = cartService.checkout(userId);

        assertNotNull(response);
        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void checkout_shouldThrowWhenCartEmpty() {
        String userId = "user1";
        Cart cart = Cart.builder().userId(userId).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(IllegalArgumentException.class, () -> cartService.checkout(userId));
    }

    @Test
    void checkout_shouldThrowWhenCartNotFound() {
        when(cartRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.checkout("unknown"));
    }

    @Test
    void getOrCreateCart_shouldCreateNewCartWhenNoneExists() {
        String userId = "newUser";
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.getOrCreateCart(userId);

        assertEquals(userId, response.getUserId());
        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getTotalPrice());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void getOrCreateCart_shouldReturnExistingCart() {
        String userId = "existingUser";
        Cart existing = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        var response = cartService.getOrCreateCart(userId);

        assertEquals(userId, response.getUserId());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_shouldThrowOnNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addItem("u1", 1L, -1));
    }

    @Test
    void concurrentAdds_shouldResultInCorrectTotalQuantity() throws InterruptedException {
        String userId = "concurrentUser";
        Long productId = 1L;
        int threadCount = 10;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(100).category("cat")
                .build();

        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Exception> exception = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cartService.addItem(userId, productId, 1);
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertNull(exception.get());
        assertEquals(threadCount, cart.getItems().get("1").getQuantity());
        assertEquals(new BigDecimal("100.00"), cart.getTotalPrice());
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

```bash
mvn test -pl cart-service -Dtest=CartServiceTest -DfailIfNoTests=false
```

Expected: All tests pass.

- [ ] **Step 3: Commit unit tests**

```bash
git add cart-service/src/test/java/com/ecommerce/cart/service/CartServiceTest.java
git commit -m "test(cart): add CartService unit tests with concurrent add test"
```

---

### Task 10: Integration Tests for CartController

**Files:**
- Create: `cart-service/src/test/java/com/ecommerce/cart/controller/CartControllerTest.java`

- [ ] **Step 1: Write `CartControllerTest.java`**

File: `cart-service/src/test/java/com/ecommerce/cart/controller/CartControllerTest.java`

```java
package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @Test
    void getOrCreateCart_shouldReturn201() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.getOrCreateCart("user1")).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user1"));
    }

    @Test
    void addItem_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId("1").quantity(2).price(BigDecimal.TEN)
                        .subtotal(new BigDecimal("20.00")).build()))
                .totalPrice(new BigDecimal("20.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.addItem(eq("user1"), eq(1L), eq(2))).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value("1"))
                .andExpect(jsonPath("$.totalPrice").value(20.00));
    }

    @Test
    void addItem_shouldReturn400WhenQuantityInvalid() throws Exception {
        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_shouldReturn503WhenProductLookupFails() throws Exception {
        when(cartService.addItem(eq("user1"), eq(1L), eq(1)))
                .thenThrow(new ProductLookupException("Product not found: 1"));

        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void updateItemQuantity_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId("1").quantity(5).price(BigDecimal.TEN)
                        .subtotal(new BigDecimal("50.00")).build()))
                .totalPrice(new BigDecimal("50.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.updateItemQuantity(eq("user1"), eq(1L), eq(5))).thenReturn(response);

        mockMvc.perform(put("/api/carts/users/{userId}/items/{productId}", "user1", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void removeItem_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.removeItem("user1", 1L)).thenReturn(response);

        mockMvc.perform(delete("/api/carts/users/{userId}/items/{productId}", "user1", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(0));
    }

    @Test
    void removeItem_shouldReturn404WhenItemNotFound() throws Exception {
        when(cartService.removeItem("user1", 999L))
                .thenThrow(new CartItemNotFoundException("Item not found: 999"));

        mockMvc.perform(delete("/api/carts/users/{userId}/items/{productId}", "user1", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCart_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.getCart("user1")).thenReturn(response);

        mockMvc.perform(get("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"));
    }

    @Test
    void getCart_shouldReturn404WhenNotFound() throws Exception {
        when(cartService.getCart("unknown"))
                .thenThrow(new CartNotFoundException("Cart not found for user: unknown"));

        mockMvc.perform(get("/api/carts/users/{userId}", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void checkout_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId("1").quantity(1).price(BigDecimal.TEN)
                        .subtotal(BigDecimal.TEN).build()))
                .totalPrice(BigDecimal.TEN)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.checkout("user1")).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(10));
    }

    @Test
    void checkout_shouldReturn400WhenCartEmpty() throws Exception {
        when(cartService.checkout("user1"))
                .thenThrow(new IllegalArgumentException("Cannot checkout with an empty cart"));

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "user1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_shouldReturn404WhenCartNotFound() throws Exception {
        when(cartService.checkout("unknown"))
                .thenThrow(new CartNotFoundException("Cart not found for user: unknown"));

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "unknown"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

```bash
mvn test -pl cart-service -Dtest=CartControllerTest -DfailIfNoTests=false
```

Expected: All tests pass.

- [ ] **Step 3: Commit integration tests**

```bash
git add cart-service/src/test/java/com/ecommerce/cart/controller/CartControllerTest.java
git commit -m "test(cart): add CartController integration tests"
```

---

### Task 11: Update Root POM, Gateway, Docker Compose

**Files:**
- Modify: `pom.xml` (root)
- Modify: `api-gateway-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add cart-service module to root `pom.xml`**

Add `<module>cart-service</module>` after the last existing module in the `<modules>` block.

Expected change in root `pom.xml`:
```xml
<modules>
    <module>api-gateway-service</module>
    <module>user-service</module>
    <module>product-service</module>
    <module>eureka-server-service</module>
    <module>shared-library</module>
    <module>cart-service</module>
</modules>
```

- [ ] **Step 2: Add cart route to API Gateway**

Add to `api-gateway-service/src/main/resources/application.yml` in the `spring.cloud.gateway.routes` list:

```yaml
        - id: cart-service
          uri: lb://cart-service
          predicates:
            - Path=/api/carts/**
```

- [ ] **Step 3: Add cart-service to `docker-compose.yml`**

Add after the `product-service` entry:

```yaml
  cart-service:
    build:
      context: .
      dockerfile: cart-service/Dockerfile
    ports:
      - "8083:8083"
    environment:
      EUREKA_URI: http://eureka-server-service:8761/eureka/
      INSTANCE_HOSTNAME: cart-service
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      eureka-server-service:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8083/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - ecommerce-network
```

- [ ] **Step 4: Run full test suite to verify everything compiles**

```bash
mvn compile -pl cart-service -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit infrastructure changes**

```bash
git add pom.xml api-gateway-service/src/main/resources/application.yml docker-compose.yml
git commit -m "feat(cart): register cart-service in root pom, gateway routes, and docker-compose"
```

---

### Task 12: Run All Cart Service Tests

- [ ] **Step 1: Run all cart-service tests**

```bash
mvn test -pl cart-service -DfailIfNoTests=false
```

Expected: All unit and integration tests pass.

- [ ] **Step 2: Final commit with any test fixes**

```bash
git add -A
git commit -m "fix(cart): resolve test issues and finalize implementation"
```
