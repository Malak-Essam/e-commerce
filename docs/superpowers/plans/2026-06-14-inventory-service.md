# Inventory Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Inventory Service that tracks product stock and handles the reservation/confirmation/cancellation lifecycle for orders, with optimistic locking for concurrent safety.

**Architecture:** Two JPA entities (Inventory, InventoryReservation) with a service layer that manages stock reservation, confirmation, and cancellation. Optimistic locking with @Version prevents overselling under concurrency. Retry logic handles OptimisticLockingFailureException. Events publish lifecycle state changes to RabbitMQ via shared-library EventPublisher.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring Data JPA, H2, Spring Cloud Stream + RabbitMQ, Eureka Client, Lombok, SpringDoc, JUnit 5 + Mockito + MockMvc

---

### Task 1: Setup Git Feature Branch

**Branch:** `feature/inventory-service`

- [ ] **Step 1: Create feature branch**

Run: `git flow feature start inventory-service`

If git flow is not available, run: `git checkout -b feature/inventory-service`

Expected: Created branch `feature/inventory-service` and switched to it.

---

### Task 2: Create Shared Library Events

**Files:**
- Create: `shared-library/src/main/java/com/ecommerce/shared/event/InventoryConfirmedEvent.java`
- Create: `shared-library/src/main/java/com/ecommerce/shared/event/InventoryReservationCancelledEvent.java`
- Create: `shared-library/src/main/java/com/ecommerce/shared/event/OrderCancelledEvent.java`
- Modify: `shared-library/src/main/java/com/ecommerce/shared/event/DomainEvent.java`
- Modify: `shared-library/src/main/java/com/ecommerce/shared/event/EventConstants.java`
- Modify: `shared-library/src/main/java/com/ecommerce/shared/event/RabbitMQEventPublisher.java`

- [ ] **Step 1: Create InventoryConfirmedEvent.java**

```java
package com.ecommerce.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class InventoryConfirmedEvent extends DomainEvent {
    private String productId;
    private String reservationId;
    private String orderId;

    public InventoryConfirmedEvent(String productId, String reservationId, String orderId) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), productId);
        this.productId = productId;
        this.reservationId = reservationId;
        this.orderId = orderId;
    }
}
```

- [ ] **Step 2: Create InventoryReservationCancelledEvent.java**

```java
package com.ecommerce.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class InventoryReservationCancelledEvent extends DomainEvent {
    private String productId;
    private String reservationId;
    private String orderId;

    public InventoryReservationCancelledEvent(String productId, String reservationId, String orderId) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), productId);
        this.productId = productId;
        this.reservationId = reservationId;
        this.orderId = orderId;
    }
}
```

- [ ] **Step 3: Create OrderCancelledEvent.java**

```java
package com.ecommerce.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class OrderCancelledEvent extends DomainEvent {
    private String orderId;
    private String reason;

    public OrderCancelledEvent(String orderId, String reason) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), orderId);
        this.orderId = orderId;
        this.reason = reason;
    }
}
```

- [ ] **Step 4: Read DomainEvent.java to see existing @JsonSubTypes**

Run: `grep -n "JsonSubTypes" shared-library/src/main/java/com/ecommerce/shared/event/DomainEvent.java`

- [ ] **Step 5: Add new events to @JsonSubTypes in DomainEvent.java**

Edit the file to add the three new event types to the `@JsonSubTypes` annotation. Find the existing `@JsonSubTypes` block and add:

```java
@JsonSubTypes.Type(value = InventoryConfirmedEvent.class, name = "InventoryConfirmedEvent"),
@JsonSubTypes.Type(value = InventoryReservationCancelledEvent.class, name = "InventoryReservationCancelledEvent"),
@JsonSubTypes.Type(value = OrderCancelledEvent.class, name = "OrderCancelledEvent"),
```

- [ ] **Step 6: Read EventConstants.java and add queue constants**

Run: `cat shared-library/src/main/java/com/ecommerce/shared/event/EventConstants.java`

Add after the existing inventory constants:
```java
public static final String INVENTORY_CONFIRMED_QUEUE = "inventory.confirmed.queue";
public static final String INVENTORY_CANCELLED_QUEUE = "inventory.cancelled.queue";
public static final String ORDER_CANCELLED_QUEUE = "order.cancelled.queue";
```

- [ ] **Step 7: Read RabbitMQEventPublisher.java and add new event bindings**

Run: `cat shared-library/src/main/java/com/ecommerce/shared/event/RabbitMQEventPublisher.java`

Find the `resolveBinding` method. Add after the existing inventory `if` blocks:
```java
if (event instanceof InventoryConfirmedEvent) {
    return "inventoryConfirmed-out-0";
}
if (event instanceof InventoryReservationCancelledEvent) {
    return "inventoryReservationCancelled-out-0";
}
if (event instanceof OrderCancelledEvent) {
    return "orderCancelled-out-0";
}
```

- [ ] **Step 8: Build shared-library to verify compilation**

Run: `mvn compile -pl shared-library -am`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add shared-library/src/main/java/com/ecommerce/shared/event/InventoryConfirmedEvent.java
git add shared-library/src/main/java/com/ecommerce/shared/event/InventoryReservationCancelledEvent.java
git add shared-library/src/main/java/com/ecommerce/shared/event/OrderCancelledEvent.java
git add shared-library/src/main/java/com/ecommerce/shared/event/DomainEvent.java
git add shared-library/src/main/java/com/ecommerce/shared/event/EventConstants.java
git add shared-library/src/main/java/com/ecommerce/shared/event/RabbitMQEventPublisher.java
git commit -m "feat(shared): add InventoryConfirmedEvent, InventoryReservationCancelledEvent, OrderCancelledEvent"
```

---

### Task 3: Add inventory-service Module and Scaffolding

**Files:**
- Create: `inventory-service/pom.xml`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/InventoryServiceApplication.java`
- Create: `inventory-service/src/main/resources/application.yml`
- Create: `inventory-service/Dockerfile`
- Modify: `pom.xml` (parent)

- [ ] **Step 1: Read the parent pom.xml to see module list**

Run: `grep -A 20 '<modules>' pom.xml`

- [ ] **Step 2: Add inventory-service module to parent pom.xml**

Add `<module>inventory-service</module>` to the `<modules>` list (after the existing modules).

- [ ] **Step 3: Create inventory-service directory**

```bash
mkdir -p inventory-service/src/main/java/com/ecommerce/inventory/{config,controller,dto,entity,exception,repository,service,event}
mkdir -p inventory-service/src/main/resources
mkdir -p inventory-service/src/test/java/com/ecommerce/inventory/{controller,service}
```

- [ ] **Step 4: Create inventory-service/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecommerce</groupId>
        <artifactId>ecommerce-microservices</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>inventory-service</artifactId>
    <name>inventory-service</name>
    <description>Inventory Service for stock management and reservation</description>

    <dependencies>
        <dependency>
            <groupId>com.ecommerce</groupId>
            <artifactId>shared-library</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
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
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
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
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: Create InventoryServiceApplication.java**

```java
package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

- [ ] **Step 6: Create application.yml**

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
      path: /h2-console
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

springdoc:
  api-docs:
    path: /api/inventory/v3/api-docs
  swagger-ui:
    path: /api/inventory/swagger-ui.html
```

- [ ] **Step 7: Create Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY shared-library ../shared-library
COPY inventory-service ./inventory-service
RUN apk add --no-cache maven && mvn clean package -pl inventory-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/inventory-service/target/*.jar app.jar
RUN apk add --no-cache curl
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 8: Build to verify scaffolding**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add pom.xml inventory-service/
git commit -m "feat(inventory): add inventory-service module scaffolding"
```

---

### Task 4: Create Entities and Repository

**Files:**
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/entity/Inventory.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/entity/InventoryReservation.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/entity/ReservationStatus.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryRepository.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryReservationRepository.java`

- [ ] **Step 1: Create ReservationStatus enum**

```java
package com.ecommerce.inventory.entity;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
```

- [ ] **Step 2: Create Inventory entity**

```java
package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    @Builder.Default
    private Integer reservedStock = 0;

    @Version
    private Long version;

    public Integer getAvailableStock() {
        return totalStock - reservedStock;
    }
}
```

- [ ] **Step 3: Create InventoryReservation entity**

```java
package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String reservationId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: Create InventoryRepository**

```java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(String productId);
}
```

- [ ] **Step 5: Create InventoryReservationRepository**

```java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByReservationId(String reservationId);
    Optional<InventoryReservation> findByProductIdAndOrderIdAndStatus(String productId, String orderId, ReservationStatus status);
    List<InventoryReservation> findByOrderId(String orderId);
}
```

- [ ] **Step 6: Build to verify compilation**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add inventory-service/src/main/java/com/ecommerce/inventory/entity/
git add inventory-service/src/main/java/com/ecommerce/inventory/repository/
git commit -m "feat(inventory): add Inventory and InventoryReservation entities with repositories"
```

---

### Task 5: Create DTOs and Exceptions

**Files:**
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/dto/InventoryResponse.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/dto/ReserveRequest.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/dto/ReserveResponse.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/dto/ConfirmRequest.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/dto/CancelRequest.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/ProductNotFoundException.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/InsufficientStockException.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/ReservationNotFoundException.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/InvalidReservationStatusException.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/ReservationConflictException.java`
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create InventoryResponse DTO**

```java
package com.ecommerce.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String productId;
    private Integer totalStock;
    private Integer reservedStock;
    private Integer availableStock;
}
```

- [ ] **Step 2: Create ReserveRequest DTO**

```java
package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}
```

- [ ] **Step 3: Create ReserveResponse DTO**

```java
package com.ecommerce.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveResponse {
    private String reservationId;
    private String productId;
    private int quantity;
    private String status;
}
```

- [ ] **Step 4: Create ConfirmRequest DTO**

```java
package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRequest {
    @NotBlank(message = "Reservation ID is required")
    private String reservationId;
}
```

- [ ] **Step 5: Create CancelRequest DTO**

```java
package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {
    @NotBlank(message = "Reservation ID is required")
    private String reservationId;
}
```

- [ ] **Step 6: Create ProductNotFoundException**

```java
package com.ecommerce.inventory.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
```

- [ ] **Step 7: Create InsufficientStockException**

```java
package com.ecommerce.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    private final int requested;
    private final int available;

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock for product " + productId + ". Requested: " + requested + ", Available: " + available);
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
```

- [ ] **Step 8: Create ReservationNotFoundException**

```java
package com.ecommerce.inventory.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String reservationId) {
        super("Reservation not found: " + reservationId);
    }
}
```

- [ ] **Step 9: Create InvalidReservationStatusException**

```java
package com.ecommerce.inventory.exception;

public class InvalidReservationStatusException extends RuntimeException {
    public InvalidReservationStatusException(String reservationId, String currentStatus, String expectedStatus) {
        super("Reservation " + reservationId + " has status " + currentStatus + ", expected " + expectedStatus);
    }
}
```

- [ ] **Step 10: Create ReservationConflictException**

```java
package com.ecommerce.inventory.exception;

public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String productId, String message) {
        super("Reservation conflict for product " + productId + ": " + message);
    }
}
```

- [ ] **Step 11: Create GlobalExceptionHandler**

```java
package com.ecommerce.inventory.exception;

import com.ecommerce.shared.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotFound(ReservationNotFoundException ex, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidReservationStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatus(InvalidReservationStatusException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ErrorResponse> handleReservationConflict(ReservationConflictException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation failed")
                .path(request.getDescription(false).replace("uri=", ""))
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(response, status);
    }
}
```

- [ ] **Step 12: Build to verify compilation**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 13: Commit**

```bash
git add inventory-service/src/main/java/com/ecommerce/inventory/dto/
git add inventory-service/src/main/java/com/ecommerce/inventory/exception/
git commit -m "feat(inventory): add DTOs and custom exceptions with global handler"
```

---

### Task 6: Create InventoryService

**Files:**
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/service/InventoryService.java`

- [ ] **Step 1: Create InventoryService with full business logic**

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.*;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.shared.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;

    private static final int MAX_RETRIES = 3;

    @Transactional(readOnly = true)
    public InventoryResponse checkStock(String productId) {
        Inventory inventory = findInventoryOrThrow(productId);
        return mapToResponse(inventory);
    }

    @Transactional(readOnly = true)
    public int getAvailableQuantity(String productId) {
        Inventory inventory = findInventoryOrThrow(productId);
        return inventory.getAvailableStock();
    }

    @Transactional
    public ReserveResponse reserveStock(String productId, String orderId, int quantity) {
        return retryOnLockFailure(productId, () -> {
            Inventory inventory = findInventoryOrThrow(productId);

            if (quantity > inventory.getAvailableStock()) {
                eventPublisher.publish(new InventoryReservationFailedEvent(
                        productId, quantity, inventory.getAvailableStock()));
                throw new InsufficientStockException(productId, quantity, inventory.getAvailableStock());
            }

            String reservationId = UUID.randomUUID().toString();

            InventoryReservation reservation = InventoryReservation.builder()
                    .reservationId(reservationId)
                    .productId(productId)
                    .orderId(orderId)
                    .quantity(quantity)
                    .status(ReservationStatus.PENDING)
                    .build();

            inventory.setReservedStock(inventory.getReservedStock() + quantity);

            inventoryRepository.save(inventory);
            reservationRepository.save(reservation);

            eventPublisher.publish(new InventoryReservedEvent(productId, quantity));

            log.info("Reserved {} units of product {} for order {} (reservation: {})",
                    quantity, productId, orderId, reservationId);

            return ReserveResponse.builder()
                    .reservationId(reservationId)
                    .productId(productId)
                    .quantity(quantity)
                    .status(ReservationStatus.PENDING.name())
                    .build();
        });
    }

    @Transactional
    public void confirmReservation(String reservationId) {
        InventoryReservation reservation = findReservationOrThrow(reservationId);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new InvalidReservationStatusException(
                    reservationId, reservation.getStatus().name(), ReservationStatus.PENDING.name());
        }

        Inventory inventory = findInventoryOrThrow(reservation.getProductId());

        reservation.setStatus(ReservationStatus.CONFIRMED);
        inventory.setTotalStock(inventory.getTotalStock() - reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        eventPublisher.publish(new InventoryConfirmedEvent(
                reservation.getProductId(), reservationId, reservation.getOrderId()));

        log.info("Confirmed reservation {} for product {} (order: {})",
                reservationId, reservation.getProductId(), reservation.getOrderId());
    }

    @Transactional
    public void cancelReservation(String reservationId) {
        InventoryReservation reservation = findReservationOrThrow(reservationId);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new InvalidReservationStatusException(
                    reservationId, reservation.getStatus().name(), ReservationStatus.PENDING.name());
        }

        Inventory inventory = findInventoryOrThrow(reservation.getProductId());

        reservation.setStatus(ReservationStatus.CANCELLED);
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        eventPublisher.publish(new InventoryReservationCancelledEvent(
                reservation.getProductId(), reservationId, reservation.getOrderId()));

        log.info("Cancelled reservation {} for product {} (order: {})",
                reservationId, reservation.getProductId(), reservation.getOrderId());
    }

    @Transactional
    public void cancelPendingReservationsByOrderId(String orderId) {
        reservationRepository.findByOrderId(orderId).stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .forEach(r -> cancelReservation(r.getReservationId()));
    }

    private Inventory findInventoryOrThrow(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private InventoryReservation findReservationOrThrow(String reservationId) {
        return reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .totalStock(inventory.getTotalStock())
                .reservedStock(inventory.getReservedStock())
                .availableStock(inventory.getAvailableStock())
                .build();
    }

    private ReserveResponse retryOnLockFailure(String productId, LockAction action) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute();
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                log.warn("Optimistic lock failure for product {} (attempt {}/{})",
                        productId, attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ReservationConflictException(productId, "Retry interrupted");
                    }
                }
            }
        }
        throw new ReservationConflictException(productId,
                "Failed after " + MAX_RETRIES + " retries due to lock contention");
    }

    @FunctionalInterface
    private interface LockAction {
        ReserveResponse execute();
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/main/java/com/ecommerce/inventory/service/
git commit -m "feat(inventory): add InventoryService with reservation logic and optimistic locking retry"
```

---

### Task 7: Create Event Handler

**Files:**
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/event/OrderCancelledEventHandler.java`

- [ ] **Step 1: Create the event handler**

```java
package com.ecommerce.inventory.event;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.shared.event.EventConsumer;
import com.ecommerce.shared.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledEventHandler implements Consumer<OrderCancelledEvent> {

    private final InventoryService inventoryService;

    @Override
    public void accept(OrderCancelledEvent event) {
        log.info("Received order cancelled event for order: {}", event.getOrderId());
        inventoryService.cancelPendingReservationsByOrderId(event.getOrderId());
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/main/java/com/ecommerce/inventory/event/
git commit -m "feat(inventory): add OrderCancelledEventHandler to release stock on order cancellation"
```

---

### Task 8: Create InventoryController

**Files:**
- Create: `inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryController.java`

- [ ] **Step 1: Create InventoryController**

```java
package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory and stock management endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products/{productId}")
    @Operation(summary = "Check stock for a product")
    @ApiResponse(responseCode = "200", description = "Stock details returned")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<InventoryResponse> checkStock(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.checkStock(productId));
    }

    @GetMapping("/products/{productId}/available")
    @Operation(summary = "Get available quantity for a product")
    @ApiResponse(responseCode = "200", description = "Available quantity returned")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Integer> getAvailableQuantity(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getAvailableQuantity(productId));
    }

    @PostMapping("/products/{productId}/reserve")
    @Operation(summary = "Reserve stock for an order")
    @ApiResponse(responseCode = "201", description = "Stock reserved successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient stock or invalid input")
    @ApiResponse(responseCode = "409", description = "Reservation conflict, retry")
    public ResponseEntity<ReserveResponse> reserveStock(
            @PathVariable String productId,
            @Valid @RequestBody ReserveRequest request) {
        ReserveResponse response = inventoryService.reserveStock(
                productId, request.getOrderId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/products/{productId}/confirm")
    @Operation(summary = "Confirm a reservation")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed")
    @ApiResponse(responseCode = "400", description = "Invalid reservation status")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    public ResponseEntity<Void> confirmReservation(
            @PathVariable String productId,
            @Valid @RequestBody ConfirmRequest request) {
        inventoryService.confirmReservation(request.getReservationId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/products/{productId}/cancel")
    @Operation(summary = "Cancel a reservation")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled")
    @ApiResponse(responseCode = "400", description = "Invalid reservation status")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable String productId,
            @Valid @RequestBody CancelRequest request) {
        inventoryService.cancelReservation(request.getReservationId());
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn compile -pl inventory-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/main/java/com/ecommerce/inventory/controller/
git commit -m "feat(inventory): add InventoryController with REST endpoints"
```

---

### Task 9: Write Unit Tests for InventoryService

**Files:**
- Create: `inventory-service/src/test/java/com/ecommerce/inventory/service/InventoryServiceTest.java`

- [ ] **Step 1: Create InventoryServiceTest**

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.*;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.shared.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Captor
    private ArgumentCaptor<Inventory> inventoryCaptor;

    @Captor
    private ArgumentCaptor<InventoryReservation> reservationCaptor;

    private Inventory inventory;
    private static final String PRODUCT_ID = "PROD-001";
    private static final String ORDER_ID = "ORD-001";

    @BeforeEach
    void setUp() {
        inventory = Inventory.builder()
                .id(1L)
                .productId(PRODUCT_ID)
                .totalStock(100)
                .reservedStock(0)
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("Check stock")
    class CheckStock {

        @Test
        @DisplayName("Should return correct stock details")
        void shouldReturnStockDetails() {
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));

            InventoryResponse response = inventoryService.checkStock(PRODUCT_ID);

            assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(response.getTotalStock()).isEqualTo(100);
            assertThat(response.getReservedStock()).isEqualTo(0);
            assertThat(response.getAvailableStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should throw ProductNotFoundException for unknown product")
        void shouldThrowWhenProductNotFound() {
            when(inventoryRepository.findByProductId(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.checkStock("UNKNOWN"))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("Get available quantity")
    class GetAvailableQuantity {

        @Test
        @DisplayName("Should return available quantity")
        void shouldReturnAvailableQuantity() {
            inventory.setTotalStock(50);
            inventory.setReservedStock(10);
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));

            int available = inventoryService.getAvailableQuantity(PRODUCT_ID);

            assertThat(available).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("Reserve stock")
    class ReserveStock {

        @Test
        @DisplayName("Should reserve stock successfully")
        void shouldReserveSuccessfully() {
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
            when(reservationRepository.save(any(InventoryReservation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ReserveResponse response = inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 5);

            assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(response.getQuantity()).isEqualTo(5);
            assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING.name());
            assertThat(response.getReservationId()).isNotNull();

            verify(inventoryRepository).save(inventoryCaptor.capture());
            assertThat(inventoryCaptor.getValue().getReservedStock()).isEqualTo(5);

            verify(eventPublisher). publish(any(InventoryReservedEvent.class));
        }

        @Test
        @DisplayName("Should throw InsufficientStockException when quantity exceeds available")
        void shouldThrowWhenInsufficientStock() {
            inventory.setTotalStock(10);
            inventory.setReservedStock(9);
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));

            assertThatThrownBy(() -> inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 2))
                    .isInstanceOf(InsufficientStockException.class);

            verify(eventPublisher).publish(any(InventoryReservationFailedEvent.class));
            verify(inventoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should retry on OptimisticLockingFailureException")
        void shouldRetryOnLockFailure() {
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new OptimisticLockingFailureException("lock conflict"))
                    .thenReturn(inventory);
            when(reservationRepository.save(any(InventoryReservation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ReserveResponse response = inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 5);

            assertThat(response).isNotNull();
            verify(inventoryRepository, times(2)).save(any(Inventory.class));
            verify(eventPublisher).publish(any(InventoryReservedEvent.class));
        }

        @Test
        @DisplayName("Should throw ReservationConflictException after exhausting retries")
        void shouldThrowAfterRetryExhaustion() {
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class)))
                    .thenThrow(new OptimisticLockingFailureException("lock conflict"));

            assertThatThrownBy(() -> inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 5))
                    .isInstanceOf(ReservationConflictException.class);

            verify(inventoryRepository, times(3)).save(any(Inventory.class));
        }
    }

    @Nested
    @DisplayName("Confirm reservation")
    class ConfirmReservation {

        @Test
        @DisplayName("Should confirm a PENDING reservation")
        void shouldConfirmReservation() {
            InventoryReservation reservation = InventoryReservation.builder()
                    .id(1L)
                    .reservationId("RES-001")
                    .productId(PRODUCT_ID)
                    .orderId(ORDER_ID)
                    .quantity(10)
                    .status(ReservationStatus.PENDING)
                    .build();

            inventory.setReservedStock(10);

            when(reservationRepository.findByReservationId("RES-001"))
                    .thenReturn(Optional.of(reservation));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
            when(reservationRepository.save(any(InventoryReservation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            inventoryService.confirmReservation("RES-001");

            verify(inventoryRepository).save(inventoryCaptor.capture());
            Inventory savedInventory = inventoryCaptor.getValue();
            assertThat(savedInventory.getTotalStock()).isEqualTo(90);
            assertThat(savedInventory.getReservedStock()).isEqualTo(0);

            verify(reservationRepository).save(reservationCaptor.capture());
            assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

            verify(eventPublisher).publish(any(InventoryConfirmedEvent.class));
        }

        @Test
        @DisplayName("Should throw when confirming non-PENDING reservation")
        void shouldThrowWhenNotPending() {
            InventoryReservation reservation = InventoryReservation.builder()
                    .reservationId("RES-001")
                    .status(ReservationStatus.CONFIRMED)
                    .build();

            when(reservationRepository.findByReservationId("RES-001"))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> inventoryService.confirmReservation("RES-001"))
                    .isInstanceOf(InvalidReservationStatusException.class);
        }
    }

    @Nested
    @DisplayName("Cancel reservation")
    class CancelReservation {

        @Test
        @DisplayName("Should cancel a PENDING reservation")
        void shouldCancelReservation() {
            InventoryReservation reservation = InventoryReservation.builder()
                    .id(1L)
                    .reservationId("RES-001")
                    .productId(PRODUCT_ID)
                    .orderId(ORDER_ID)
                    .quantity(10)
                    .status(ReservationStatus.PENDING)
                    .build();

            inventory.setReservedStock(10);

            when(reservationRepository.findByReservationId("RES-001"))
                    .thenReturn(Optional.of(reservation));
            when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
            when(reservationRepository.save(any(InventoryReservation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            inventoryService.cancelReservation("RES-001");

            verify(inventoryRepository).save(inventoryCaptor.capture());
            assertThat(inventoryCaptor.getValue().getReservedStock()).isEqualTo(0);

            verify(reservationRepository).save(reservationCaptor.capture());
            assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.CANCELLED);

            verify(eventPublisher).publish(any(InventoryReservationCancelledEvent.class));
        }

        @Test
        @DisplayName("Should throw when cancelling non-PENDING reservation")
        void shouldThrowWhenNotPending() {
            InventoryReservation reservation = InventoryReservation.builder()
                    .reservationId("RES-001")
                    .status(ReservationStatus.CANCELLED)
                    .build();

            when(reservationRepository.findByReservationId("RES-001"))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> inventoryService.cancelReservation("RES-001"))
                    .isInstanceOf(InvalidReservationStatusException.class);
        }
    }

    @Nested
    @DisplayName("Cancel pending reservations by order ID")
    class CancelByOrderId {

        @Test
        @DisplayName("Should cancel all PENDING reservations for an order")
        void shouldCancelPendingReservations() {
            InventoryReservation res1 = InventoryReservation.builder()
                    .id(1L).reservationId("RES-001").productId(PRODUCT_ID)
                    .orderId(ORDER_ID).quantity(5).status(ReservationStatus.PENDING)
                    .build();
            InventoryReservation res2 = InventoryReservation.builder()
                    .id(2L).reservationId("RES-002").productId("PROD-002")
                    .orderId(ORDER_ID).quantity(3).status(ReservationStatus.PENDING)
                    .build();

            Inventory inventory2 = Inventory.builder()
                    .id(2L).productId("PROD-002").totalStock(50)
                    .reservedStock(3).version(0L).build();

            when(reservationRepository.findByOrderId(ORDER_ID))
                    .thenReturn(List.of(res1, res2));
            when(reservationRepository.findByReservationId("RES-001"))
                    .thenReturn(Optional.of(res1));
            when(reservationRepository.findByReservationId("RES-002"))
                    .thenReturn(Optional.of(res2));
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                    .thenReturn(Optional.of(inventory));
            when(inventoryRepository.findByProductId("PROD-002"))
                    .thenReturn(Optional.of(inventory2));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reservationRepository.save(any(InventoryReservation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            inventoryService.cancelPendingReservationsByOrderId(ORDER_ID);

            verify(eventPublisher, times(2)).publish(any(InventoryReservationCancelledEvent.class));
        }
    }
}
```

- [ ] **Step 2: Run the unit tests**

Run: `mvn test -pl inventory-service -am`
Expected: All tests pass (green)

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/test/java/com/ecommerce/inventory/service/
git commit -m "test(inventory): add InventoryService unit tests"
```

---

### Task 10: Write Controller Tests

**Files:**
- Create: `inventory-service/src/test/java/com/ecommerce/inventory/controller/InventoryControllerTest.java`

- [ ] **Step 1: Create InventoryControllerTest** (matching cart-service's test pattern)

```java
package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.*;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PRODUCT_ID = "PROD-001";
    private static final String BASE_URL = "/api/inventory/products";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/inventory/products/{productId} should return stock details")
    void checkStock() throws Exception {
        InventoryResponse response = InventoryResponse.builder()
                .productId(PRODUCT_ID).totalStock(100).reservedStock(10).availableStock(90)
                .build();
        when(inventoryService.checkStock(PRODUCT_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.availableStock").value(90));
    }

    @Test
    @DisplayName("GET /api/inventory/products/{productId}/available should return available quantity")
    void getAvailableQuantity() throws Exception {
        when(inventoryService.getAvailableQuantity(PRODUCT_ID)).thenReturn(50);

        mockMvc.perform(get(BASE_URL + "/{productId}/available", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("50"));
    }

    @Test
    @DisplayName("POST /api/inventory/products/{productId}/reserve should create reservation")
    void reserveStock() throws Exception {
        ReserveRequest request = new ReserveRequest("ORD-001", 5);
        ReserveResponse response = ReserveResponse.builder()
                .reservationId("RES-001").productId(PRODUCT_ID)
                .quantity(5).status(ReservationStatus.PENDING.name())
                .build();
        when(inventoryService.reserveStock(eq(PRODUCT_ID), eq("ORD-001"), eq(5)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{productId}/reserve", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("RES-001"))
                .andExpect(jsonPath("$.status").value(ReservationStatus.PENDING.name()));
    }

    @Test
    @DisplayName("POST /api/inventory/products/{productId}/confirm should confirm reservation")
    void confirmReservation() throws Exception {
        ConfirmRequest request = new ConfirmRequest("RES-001");

        mockMvc.perform(post(BASE_URL + "/{productId}/confirm", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/inventory/products/{productId}/cancel should cancel reservation")
    void cancelReservation() throws Exception {
        CancelRequest request = new CancelRequest("RES-001");

        mockMvc.perform(post(BASE_URL + "/{productId}/cancel", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 404 when product not found")
    void checkStockNotFound() throws Exception {
        when(inventoryService.checkStock("UNKNOWN"))
                .thenThrow(new ProductNotFoundException("UNKNOWN"));

        mockMvc.perform(get(BASE_URL + "/{productId}", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when insufficient stock")
    void reserveInsufficientStock() throws Exception {
        ReserveRequest request = new ReserveRequest("ORD-001", 999);
        when(inventoryService.reserveStock(eq(PRODUCT_ID), eq("ORD-001"), eq(999)))
                .thenThrow(new InsufficientStockException(PRODUCT_ID, 999, 10));

        mockMvc.perform(post(BASE_URL + "/{productId}/reserve", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when validation fails on ReserveRequest")
    void reserveValidationFails() throws Exception {
        ReserveRequest request = new ReserveRequest("", 0);

        mockMvc.perform(post(BASE_URL + "/{productId}/reserve", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the controller tests**

Run: `mvn test -pl inventory-service -am`
Expected: All tests pass (green)

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/test/java/com/ecommerce/inventory/controller/
git commit -m "test(inventory): add InventoryController integration tests"
```

---

### Task 11: Write Integration Tests

**Files:**
- Create: `inventory-service/src/test/java/com/ecommerce/inventory/InventoryServiceIntegrationTest.java`

- [ ] **Step 1: Create InventoryServiceIntegrationTest**

```java
package com.ecommerce.inventory;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private static final String PRODUCT_ID = "INTEGRATION-PROD-001";
    private static final String ORDER_ID = "INTEGRATION-ORD-001";
    private static final String ORDER_ID_2 = "INTEGRATION-ORD-002";

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        Inventory inventory = Inventory.builder()
                .productId(PRODUCT_ID)
                .totalStock(100)
                .reservedStock(0)
                .build();
        inventoryRepository.save(inventory);
    }

    @Test
    @DisplayName("Should complete reserve -> confirm flow")
    void reserveThenConfirm() {
        ReserveResponse reserveResponse = inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 10);

        assertThat(reserveResponse.getReservationId()).isNotNull();
        assertThat(reserveResponse.getQuantity()).isEqualTo(10);

        InventoryResponse beforeConfirm = inventoryService.checkStock(PRODUCT_ID);
        assertThat(beforeConfirm.getAvailableStock()).isEqualTo(90);
        assertThat(beforeConfirm.getReservedStock()).isEqualTo(10);

        inventoryService.confirmReservation(reserveResponse.getReservationId());

        InventoryResponse afterConfirm = inventoryService.checkStock(PRODUCT_ID);
        assertThat(afterConfirm.getTotalStock()).isEqualTo(90);
        assertThat(afterConfirm.getReservedStock()).isEqualTo(0);
        assertThat(afterConfirm.getAvailableStock()).isEqualTo(90);
    }

    @Test
    @DisplayName("Should complete reserve -> cancel flow")
    void reserveThenCancel() {
        ReserveResponse reserveResponse = inventoryService.reserveStock(PRODUCT_ID, ORDER_ID, 20);

        InventoryResponse beforeCancel = inventoryService.checkStock(PRODUCT_ID);
        assertThat(beforeCancel.getReservedStock()).isEqualTo(20);

        inventoryService.cancelReservation(reserveResponse.getReservationId());

        InventoryResponse afterCancel = inventoryService.checkStock(PRODUCT_ID);
        assertThat(afterCancel.getTotalStock()).isEqualTo(100);
        assertThat(afterCancel.getReservedStock()).isEqualTo(0);
        assertThat(afterCancel.getAvailableStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should prevent overselling under concurrent reservations")
    void shouldPreventOverselling() throws InterruptedException {
        int threadCount = 20;
        int quantityPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String orderId = "CONCURRENT-ORD-" + i;
            executor.submit(() -> {
                try {
                    inventoryService.reserveStock(PRODUCT_ID, orderId, quantityPerThread);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        int totalReserved = successCount.get() * quantityPerThread;
        assertThat(totalReserved).isLessThanOrEqualTo(100);
        assertThat(failureCount.get()).isPositive();

        InventoryResponse response = inventoryService.checkStock(PRODUCT_ID);
        assertThat(response.getAvailableStock()).isGreaterThanOrEqualTo(0);
    }
}
```

- [ ] **Step 2: Run all tests**

Run: `mvn test -pl inventory-service -am`
Expected: All tests pass (green)

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/test/java/com/ecommerce/inventory/InventoryServiceIntegrationTest.java
git commit -m "test(inventory): add integration tests for reservation flow and concurrent overselling prevention"
```

---

### Task 12: Register in API Gateway and Docker Compose

**Files:**
- Modify: `api-gateway-service/src/main/resources/application.yml`

- [ ] **Step 1: Read the API gateway config**

Run: `cat api-gateway-service/src/main/resources/application.yml`

- [ ] **Step 2: Add inventory-service route**

Add to the `spring.cloud.gateway.routes` list:
```yaml
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
```

- [ ] **Step 3: Read docker-compose.yml**

Run: `cat docker-compose.yml`

- [ ] **Step 4: Add inventory-service to docker-compose.yml**

Add after the cart-service entry:
```yaml
  inventory-service:
    build:
      context: .
      dockerfile: inventory-service/Dockerfile
    ports:
      - "8084:8084"
    environment:
      EUREKA_URI: http://eureka-server-service:8761/eureka/
      INSTANCE_HOSTNAME: inventory-service
      RABBIT_HOST: rabbitmq
      RABBIT_PORT: 5672
    depends_on:
      - eureka-server-service
      - rabbitmq
```

- [ ] **Step 5: Commit**

```bash
git add api-gateway-service/src/main/resources/application.yml
git add docker-compose.yml
git commit -m "feat(inventory): register inventory-service in gateway and docker-compose"
```

---

### Task 13: Final Verification

- [ ] **Step 1: Run all tests across entire project**

Run: `mvn test -pl inventory-service -am`
Expected: BUILD SUCCESS (all tests pass)

- [ ] **Step 2: Build the entire project**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify git status**

Run: `git status`
Expected: Working tree clean (all changes committed)

- [ ] **Step 4: Return to master branch**

Run: `git checkout master`
Expected: Switched to master
