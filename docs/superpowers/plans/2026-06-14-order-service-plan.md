# Order Service — Saga Pattern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Order Service with Saga pattern (choreography style) and a minimal Payment Service, integrating with existing Cart, Inventory, Product services.

**Architecture:** Hybrid choreography saga — OrderSaga class manages state machine internally, REST calls for critical path (fetch cart, reserve inventory, process payment), events for audit trail and decoupled side-effects (clear cart, release inventory).

**Tech Stack:** Spring Boot 3.4.4, Spring Cloud 2024.0.1, Spring Cloud Stream + RabbitMQ, JPA/H2, WebClient + Resilience4j, Java 21, Maven

---

### Task 1: Git Flow Setup and Module Registration

**Files:**
- Modify: `pom.xml` (parent, add modules)
- Create: `order-service/`, `payment-service/` directories

- [ ] **Step 1: Install git-flow and initialize**

```bash
sudo apt-get update && sudo apt-get install -y git-flow && git flow init -d
```

Expected: Git flow initialized with `develop` branch, `master` and `develop` branches exist.

- [ ] **Step 2: Start feature branch**

```bash
git checkout develop
git flow feature start order-service
```

Expected: On branch `feature/order-service`.

- [ ] **Step 3: Add modules to parent pom.xml**

Edit `pom.xml` — add `order-service` and `payment-service` to the `<modules>` section:

```xml
<module>order-service</module>
<module>payment-service</module>
```

- [ ] **Step 4: Create service directories**

```bash
mkdir -p order-service/src/main/java/com/ecommerce/order/{config,entity/enums,repository,dto,controller,service,client,event,exception}
mkdir -p order-service/src/main/resources
mkdir -p order-service/src/test/java/com/ecommerce/order
mkdir -p payment-service/src/main/java/com/ecommerce/payment/{config,controller,dto,service,event,exception}
mkdir -p payment-service/src/main/resources
mkdir -p payment-service/src/test/java/com/ecommerce/payment
```

Expected: All package directories exist.

---

### Task 2: Payment Service — Scaffolding and Core Logic

**Files:**
- Create: `payment-service/pom.xml`
- Create: `payment-service/src/main/resources/application.yml`
- Create: `payment-service/src/main/java/com/ecommerce/payment/PaymentServiceApplication.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/config/PaymentConfig.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/dto/PaymentRequest.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/dto/PaymentResponse.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/service/PaymentService.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/controller/PaymentController.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/event/PaymentEventPublisher.java`
- Create: `payment-service/src/main/java/com/ecommerce/payment/exception/GlobalExceptionHandler.java`
- Create: `payment-service/Dockerfile`

- [ ] **Step 1: Create payment-service/pom.xml**

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

    <artifactId>payment-service</artifactId>
    <name>Payment Service</name>
    <description>Minimal payment processing service for saga demonstration</description>

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
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ecommerce</groupId>
            <artifactId>shared-library</artifactId>
            <version>${project.version}</version>
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
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create payment-service/src/main/resources/application.yml**

```yaml
server:
  port: 8086

spring:
  application:
    name: payment-service
  cloud:
    stream:
      rabbit:
        default:
          consumer:
            auto-bind-dlq: true
            republish-to-dlq: true
      bindings:
        paymentProcessed-out-0:
          destination: payment.events

eureka:
  instance:
    hostname: ${INSTANCE_HOSTNAME:localhost}
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 3: Create PaymentServiceApplication.java**

```java
package com.ecommerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create PaymentConfig.java**

```java
package com.ecommerce.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadLocalRandom;

@Configuration
public class PaymentConfig {

    @Bean
    public ThreadLocalRandom threadLocalRandom() {
        return ThreadLocalRandom.current();
    }
}
```

- [ ] **Step 5: Create DTOs**

`PaymentRequest.java`:
```java
package com.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
}
```

`PaymentResponse.java`:
```java
package com.ecommerce.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long paymentId;
    private String orderId;
    private BigDecimal amount;
    private boolean success;
    private String message;
}
```

- [ ] **Step 6: Create PaymentService.java**

```java
package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.event.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final AtomicLong idCounter = new AtomicLong(1);
    private final PaymentEventPublisher eventPublisher;

    public PaymentResponse processPayment(PaymentRequest request) {
        long paymentId = idCounter.getAndIncrement();
        boolean success = ThreadLocalRandom.current().nextDouble() < 0.9;

        log.info("Processing payment {} for order {}: {} (amount={})",
            paymentId, request.getOrderId(), success ? "APPROVED" : "DECLINED", request.getAmount());

        PaymentResponse response = PaymentResponse.builder()
            .paymentId(paymentId)
            .orderId(request.getOrderId())
            .amount(request.getAmount())
            .success(success)
            .message(success ? "Payment approved" : "Payment declined")
            .build();

        eventPublisher.publishPaymentProcessed(response);
        return response;
    }
}
```

- [ ] **Step 7: Create PaymentEventPublisher.java**

```java
package com.ecommerce.payment.event;

import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.shared.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.ecommerce.shared.event.EventPublisher;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final EventPublisher eventPublisher;

    public void publishPaymentProcessed(PaymentResponse response) {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
            response.getOrderId(),
            response.getPaymentId(),
            Long.valueOf(response.getOrderId()),
            response.getAmount(),
            response.isSuccess()
        );
        log.info("Publishing PaymentProcessedEvent for order: {}", response.getOrderId());
        eventPublisher.publish(event);
    }
}
```

- [ ] **Step 8: Create PaymentController.java**

```java
package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 9: Create GlobalExceptionHandler.java**

```java
package com.ecommerce.payment.exception;

import com.ecommerce.shared.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage()));
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation failed")
            .path("/api/payments/process")
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

- [ ] **Step 10: Create payment-service/Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY shared-library shared-library/
COPY payment-service payment-service/
RUN mvn -pl payment-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/payment-service/target/*.jar app.jar
EXPOSE 8086
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -f http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 11: Verify payment-service builds**

```bash
mvn -pl payment-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 3: Payment Service — Tests

**Files:**
- Create: `payment-service/src/test/java/com/ecommerce/payment/service/PaymentServiceTest.java`

- [ ] **Step 1: Write PaymentServiceTest**

```java
package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.event.PaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<PaymentResponse> responseCaptor;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(eventPublisher);
    }

    @Test
    void processPayment_ShouldReturnResponse() {
        PaymentRequest request = PaymentRequest.builder()
            .orderId("order-123")
            .amount(new BigDecimal("99.99"))
            .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertNotNull(response.getPaymentId());
        assertEquals("order-123", response.getOrderId());
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        assertNotNull(response.getMessage());

        verify(eventPublisher).publishPaymentProcessed(responseCaptor.capture());
        PaymentResponse captured = responseCaptor.getValue();
        assertEquals(response.getPaymentId(), captured.getPaymentId());
    }
}
```

- [ ] **Step 2: Run payment-service tests**

```bash
mvn -pl payment-service test -q
```

Expected: Tests pass.

---

### Task 4: Order Service — Scaffolding

**Files:**
- Create: `order-service/pom.xml`
- Create: `order-service/src/main/resources/application.yml`
- Create: `order-service/src/main/java/com/ecommerce/order/OrderServiceApplication.java`
- Create: `order-service/src/main/java/com/ecommerce/order/config/OrderConfig.java`
- Create: `order-service/Dockerfile`

- [ ] **Step 1: Create order-service/pom.xml**

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

    <artifactId>order-service</artifactId>
    <name>Order Service</name>
    <description>Order service with Saga pattern for distributed transactions</description>

    <dependencies>
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
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
            <groupId>com.ecommerce</groupId>
            <artifactId>shared-library</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
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
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create order-service/src/main/resources/application.yml**

```yaml
server:
  port: 8085

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:h2:file:./data/orderdb
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
      rabbit:
        default:
          consumer:
            auto-bind-dlq: true
            republish-to-dlq: true
      bindings:
        orderCreated-out-0:
          destination: order.events
        orderConfirmed-out-0:
          destination: order.events
        orderFailed-out-0:
          destination: order.events
        orderCancelled-out-0:
          destination: order.events
        inventoryReserved-in-0:
          destination: inventory.events
          group: order-service
        inventoryReservationFailed-in-0:
          destination: inventory.events
          group: order-service
        paymentProcessed-in-0:
          destination: payment.events
          group: order-service

resilience4j:
  circuitbreaker:
    instances:
      cartService:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
      productService:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
      inventoryService:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
      paymentService:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
  retry:
    instances:
      cartService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
      productService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
      inventoryService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
      paymentService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2

eureka:
  instance:
    hostname: ${INSTANCE_HOSTNAME:localhost}
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true

management:
  endpoints:
    web:
      exposure:
        include: health

springdoc:
  api-docs:
    path: /api/orders/v3/api-docs
  swagger-ui:
    path: /api/orders/swagger-ui.html
```

- [ ] **Step 3: Create OrderServiceApplication.java**

```java
package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create OrderConfig.java**

```java
package com.ecommerce.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OrderConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

- [ ] **Step 5: Create order-service/Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY shared-library shared-library/
COPY order-service order-service/
RUN mvn -pl order-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -f http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Verify order-service compiles**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 5: Order Service — Entities and Repositories

**Files:**
- Create: `order-service/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java`
- Create: `order-service/src/main/java/com/ecommerce/order/entity/Order.java`
- Create: `order-service/src/main/java/com/ecommerce/order/entity/OrderItem.java`
- Create: `order-service/src/main/java/com/ecommerce/order/entity/OrderEvent.java`
- Create: `order-service/src/main/java/com/ecommerce/order/repository/OrderRepository.java`
- Create: `order-service/src/main/java/com/ecommerce/order/repository/OrderItemRepository.java`
- Create: `order-service/src/main/java/com/ecommerce/order/repository/OrderEventRepository.java`

- [ ] **Step 1: Create OrderStatus enum**

```java
package com.ecommerce.order.entity.enums;

public enum OrderStatus {
    PENDING,
    CART_VALIDATED,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 2: Create Order entity**

```java
package com.ecommerce.order.entity;

import com.ecommerce.order.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderId == null) {
            orderId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (items == null) {
            items = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create OrderItem entity**

```java
package com.ecommerce.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;
}
```

- [ ] **Step 4: Create OrderEvent entity**

```java
package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 5: Create repositories**

`OrderRepository.java`:
```java
package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderId(String orderId);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);
}
```

`OrderItemRepository.java`:
```java
package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
}
```

`OrderEventRepository.java`:
```java
package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderIdOrderByTimestampAsc(String orderId);
}
```

- [ ] **Step 6: Verify compilation**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 6: Order Service — DTOs and Exceptions

**Files:**
- Create: `order-service/src/main/java/com/ecommerce/order/dto/OrderRequest.java`
- Create: `order-service/src/main/java/com/ecommerce/order/dto/OrderResponse.java`
- Create: `order-service/src/main/java/com/ecommerce/order/dto/OrderItemResponse.java`
- Create: `order-service/src/main/java/com/ecommerce/order/exception/OrderNotFoundException.java`
- Create: `order-service/src/main/java/com/ecommerce/order/exception/InvalidOrderStateException.java`
- Create: `order-service/src/main/java/com/ecommerce/order/exception/SagaStepFailedException.java`
- Create: `order-service/src/main/java/com/ecommerce/order/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create OrderRequest.java**

```java
package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
}
```

- [ ] **Step 2: Create OrderItemResponse.java**

```java
package com.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
```

- [ ] **Step 3: Create OrderResponse.java**

```java
package com.ecommerce.order.dto;

import com.ecommerce.order.entity.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private Long userId;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
}
```

- [ ] **Step 4: Create exception classes**

`OrderNotFoundException.java`:
```java
package com.ecommerce.order.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
```

`InvalidOrderStateException.java`:
```java
package com.ecommerce.order.exception;

import com.ecommerce.order.entity.enums.OrderStatus;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String orderId, OrderStatus current, String action) {
        super(String.format("Order %s is in %s state, cannot %s", orderId, current, action));
    }
}
```

`SagaStepFailedException.java`:
```java
package com.ecommerce.order.exception;

public class SagaStepFailedException extends RuntimeException {
    private final String step;
    private final String details;

    public SagaStepFailedException(String step, String details) {
        super("Saga step '" + step + "' failed: " + details);
        this.step = step;
        this.details = details;
    }

    public String getStep() { return step; }
    public String getDetails() { return details; }
}
```

- [ ] **Step 5: Create GlobalExceptionHandler.java**

```java
package com.ecommerce.order.exception;

import com.ecommerce.shared.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidOrderStateException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(SagaStepFailedException.class)
    public ResponseEntity<ErrorResponse> handleSagaStepFailed(SagaStepFailedException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage()));
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation failed")
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(ex.getMessage())
            .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal server error: " + ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 7: Order Service — REST Clients

**Files:**
- Create: `order-service/src/main/java/com/ecommerce/order/client/CartServiceClient.java`
- Create: `order-service/src/main/java/com/ecommerce/order/client/ProductServiceClient.java`
- Create: `order-service/src/main/java/com/ecommerce/order/client/InventoryServiceClient.java`
- Create: `order-service/src/main/java/com/ecommerce/order/client/PaymentServiceClient.java`

- [ ] **Step 1: Create CartServiceClient.java**

```java
package com.ecommerce.order.client;

import com.ecommerce.order.dto.OrderItemResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CartServiceClient {

    private final WebClient webClient;

    public CartServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Retry(name = "cartService")
    @CircuitBreaker(name = "cartService", fallbackMethod = "getCartFallback")
    public Optional<CartData> getCartForCheckout(Long userId) {
        Map<String, Object> response = webClient
            .get()
            .uri("http://cart-service/api/carts/users/{userId}/checkout", userId)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(2));

        return Optional.ofNullable(response).map(CartData::fromMap);
    }

    private Optional<CartData> getCartFallback(Long userId, Throwable t) {
        log.warn("Fallback for cart checkout (user {}): {}", userId, t.getMessage());
        return Optional.empty();
    }

    public record CartData(List<CartItemData> items, BigDecimal totalPrice, String userId) {
        public static CartData fromMap(Map<String, Object> map) {
            if (map == null) return null;
            BigDecimal total = map.containsKey("totalPrice") && map.get("totalPrice") != null
                ? new BigDecimal(map.get("totalPrice").toString())
                : BigDecimal.ZERO;
            String uid = map.getOrDefault("userId", "").toString();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) map.getOrDefault("items", Collections.emptyList());
            List<CartItemData> items = rawItems.stream().map(CartItemData::fromMap).toList();

            return new CartData(items, total, uid);
        }
    }

    public record CartItemData(Long productId, int quantity, BigDecimal price, BigDecimal subtotal) {
        public static CartItemData fromMap(Map<String, Object> map) {
            Long pid = map.containsKey("productId") && map.get("productId") != null
                ? Long.valueOf(map.get("productId").toString()) : 0L;
            int qty = map.containsKey("quantity") && map.get("quantity") != null
                ? Integer.parseInt(map.get("quantity").toString()) : 0;
            BigDecimal prc = map.containsKey("price") && map.get("price") != null
                ? new BigDecimal(map.get("price").toString()) : BigDecimal.ZERO;
            BigDecimal sub = map.containsKey("subtotal") && map.get("subtotal") != null
                ? new BigDecimal(map.get("subtotal").toString()) : BigDecimal.ZERO;
            return new CartItemData(pid, qty, prc, sub);
        }
    }
}
```

- [ ] **Step 2: Create ProductServiceClient.java**

```java
package com.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ProductServiceClient {

    private final WebClient webClient;

    public ProductServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public Optional<ProductData> getProduct(Long productId) {
        Map<String, Object> response = webClient
            .get()
            .uri("http://product-service/api/products/{id}", productId)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(2));

        return Optional.ofNullable(response).map(ProductData::fromMap);
    }

    private Optional<ProductData> getProductFallback(Long productId, Throwable t) {
        log.warn("Fallback for product {}: {}", productId, t.getMessage());
        return Optional.empty();
    }

    public record ProductData(Long id, String name, BigDecimal price) {
        public static ProductData fromMap(Map<String, Object> map) {
            Long id = map.containsKey("id") && map.get("id") != null
                ? Long.valueOf(map.get("id").toString()) : 0L;
            String name = map.getOrDefault("name", "Unknown").toString();
            BigDecimal price = map.containsKey("price") && map.get("price") != null
                ? new BigDecimal(map.get("price").toString()) : BigDecimal.ZERO;
            return new ProductData(id, name, price);
        }
    }
}
```

- [ ] **Step 3: Create InventoryServiceClient.java**

```java
package com.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class InventoryServiceClient {

    private final WebClient webClient;

    public InventoryServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveFallback")
    public Optional<ReservationData> reserveStock(String productId, String orderId, int quantity) {
        Map<String, Object> body = Map.of("orderId", orderId, "quantity", quantity);
        Map<String, Object> response = webClient
            .post()
            .uri("http://inventory-service/api/inventory/products/{productId}/reserve", productId)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(2));

        return Optional.ofNullable(response).map(ReservationData::fromMap);
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "confirmFallback")
    public boolean confirmReservation(String productId, String reservationId) {
        Map<String, Object> body = Map.of("reservationId", reservationId);
        webClient
            .post()
            .uri("http://inventory-service/api/inventory/products/{productId}/confirm", productId)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(2));
        return true;
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "cancelFallback")
    public boolean cancelReservation(String productId, String reservationId) {
        Map<String, Object> body = Map.of("reservationId", reservationId);
        webClient
            .post()
            .uri("http://inventory-service/api/inventory/products/{productId}/cancel", productId)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(2));
        return true;
    }

    private Optional<ReservationData> reserveFallback(String productId, String orderId, int quantity, Throwable t) {
        log.warn("Fallback for reserve (product {}, order {}): {}", productId, orderId, t.getMessage());
        return Optional.empty();
    }

    private boolean confirmFallback(String productId, String reservationId, Throwable t) {
        log.warn("Fallback for confirm (product {}, reservation {}): {}", productId, reservationId, t.getMessage());
        return false;
    }

    private boolean cancelFallback(String productId, String reservationId, Throwable t) {
        log.warn("Fallback for cancel (product {}, reservation {}): {}", productId, reservationId, t.getMessage());
        return false;
    }

    public record ReservationData(String reservationId, String productId, int quantity, String status) {
        public static ReservationData fromMap(Map<String, Object> map) {
            String rid = map.getOrDefault("reservationId", "").toString();
            String pid = map.getOrDefault("productId", "").toString();
            int qty = map.containsKey("quantity") && map.get("quantity") != null
                ? Integer.parseInt(map.get("quantity").toString()) : 0;
            String st = map.getOrDefault("status", "").toString();
            return new ReservationData(rid, pid, qty, st);
        }
    }
}
```

- [ ] **Step 4: Create PaymentServiceClient.java**

```java
package com.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PaymentServiceClient {

    private final WebClient webClient;

    public PaymentServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Retry(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public Optional<PaymentResult> processPayment(String orderId, BigDecimal amount) {
        Map<String, Object> body = Map.of("orderId", orderId, "amount", amount);
        Map<String, Object> response = webClient
            .post()
            .uri("http://payment-service/api/payments/process")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(2));

        return Optional.ofNullable(response).map(PaymentResult::fromMap);
    }

    private Optional<PaymentResult> processPaymentFallback(String orderId, BigDecimal amount, Throwable t) {
        log.warn("Fallback for payment (order {}): {}", orderId, t.getMessage());
        return Optional.empty();
    }

    public record PaymentResult(Long paymentId, String orderId, BigDecimal amount, boolean success, String message) {
        public static PaymentResult fromMap(Map<String, Object> map) {
            Long pid = map.containsKey("paymentId") && map.get("paymentId") != null
                ? Long.valueOf(map.get("paymentId").toString()) : 0L;
            String oid = map.getOrDefault("orderId", "").toString();
            BigDecimal amt = map.containsKey("amount") && map.get("amount") != null
                ? new BigDecimal(map.get("amount").toString()) : BigDecimal.ZERO;
            boolean suc = map.containsKey("success") && map.get("success") != null
                && Boolean.parseBoolean(map.get("success").toString());
            String msg = map.getOrDefault("message", "").toString();
            return new PaymentResult(pid, oid, amt, suc, msg);
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 8: Order Service — Event Publisher and Handlers

**Files:**
- Create: `order-service/src/main/java/com/ecommerce/order/event/OrderEventPublisher.java`
- Create: `order-service/src/main/java/com/ecommerce/order/event/InventoryEventHandlers.java`
- Create: `order-service/src/main/java/com/ecommerce/order/event/PaymentEventHandlers.java`

- [ ] **Step 1: Create OrderEventPublisher.java**

```java
package com.ecommerce.order.event;

import com.ecommerce.order.entity.Order;
import com.ecommerce.shared.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final EventPublisher eventPublisher;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getOrderId(),
            order.getId(),
            order.getUserId(),
            order.getTotalPrice()
        );
        log.info("Publishing OrderCreatedEvent for order: {}", order.getOrderId());
        eventPublisher.publish(event);
    }

    public void publishOrderConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
            order.getOrderId(),
            order.getId(),
            order.getStatus().name()
        );
        log.info("Publishing OrderConfirmedEvent for order: {}", order.getOrderId());
        eventPublisher.publish(event);
    }

    public void publishOrderFailed(Order order, String reason) {
        OrderFailedEvent event = new OrderFailedEvent(
            order.getOrderId(),
            order.getId(),
            reason
        );
        log.info("Publishing OrderFailedEvent for order: {} reason: {}", order.getOrderId(), reason);
        eventPublisher.publish(event);
    }

    public void publishOrderCancelled(Order order, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(
            order.getOrderId(),
            order.getOrderId(),
            reason
        );
        log.info("Publishing OrderCancelledEvent for order: {} reason: {}", order.getOrderId(), reason);
        eventPublisher.publish(event);
    }
}
```

- [ ] **Step 2: Create InventoryEventHandlers.java**

```java
package com.ecommerce.order.event;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEvent;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.shared.event.InventoryReservationFailedEvent;
import com.ecommerce.shared.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventHandlers {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Handling InventoryReservedEvent for product: {}", event.getProductId());
    }

    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.info("Handling InventoryReservationFailedEvent for product: {}", event.getProductId());
    }
}
```

- [ ] **Step 3: Create PaymentEventHandlers.java**

```java
package com.ecommerce.order.event;

import com.ecommerce.shared.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventHandlers {

    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Handling PaymentProcessedEvent for order: {} success: {}",
            event.getOrderId(), event.isSuccess());
    }
}
```

- [ ] **Step 4: Add function bindings for event consumption**

Create `order-service/src/main/java/com/ecommerce/order/event/EventConsumerConfig.java`:

```java
package com.ecommerce.order.event;

import com.ecommerce.shared.event.InventoryReservationFailedEvent;
import com.ecommerce.shared.event.InventoryReservedEvent;
import com.ecommerce.shared.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class EventConsumerConfig {

    private final InventoryEventHandlers inventoryEventHandlers;
    private final PaymentEventHandlers paymentEventHandlers;

    @Bean
    public Consumer<InventoryReservedEvent> inventoryReserved() {
        return inventoryEventHandlers::handleInventoryReserved;
    }

    @Bean
    public Consumer<InventoryReservationFailedEvent> inventoryReservationFailed() {
        return inventoryEventHandlers::handleInventoryReservationFailed;
    }

    @Bean
    public Consumer<PaymentProcessedEvent> paymentProcessed() {
        return paymentEventHandlers::handlePaymentProcessed;
    }
}
```

- [ ] **Step 5: Verify compilation**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 9: Order Service — Saga and Business Logic

**Files:**
- Create: `order-service/src/main/java/com/ecommerce/order/service/OrderSaga.java`
- Create: `order-service/src/main/java/com/ecommerce/order/service/OrderService.java`
- Create: `order-service/src/main/java/com/ecommerce/order/controller/OrderController.java`

- [ ] **Step 1: Create OrderSaga.java**

```java
package com.ecommerce.order.service;

import com.ecommerce.order.client.CartServiceClient;
import com.ecommerce.order.client.InventoryServiceClient;
import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderEvent;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.SagaStepFailedException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSaga {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final CartServiceClient cartServiceClient;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Transactional
    public OrderResponse createOrder(Long userId) {
        Order order = Order.builder()
            .userId(userId)
            .totalPrice(BigDecimal.ZERO)
            .status(OrderStatus.PENDING)
            .items(new ArrayList<>())
            .build();
        orderRepository.save(order);

        // Publish OrderCreatedEvent immediately for other services to react (e.g., cart clearing)
        eventPublisher.publishOrderCreated(order);

        try {
            return executeSaga(order);
        } catch (Exception e) {
            log.error("Saga failed for order {}: {}", order.getOrderId(), e.getMessage());
            return failOrder(order, "Saga failed: " + e.getMessage());
        }
    }

    private OrderResponse executeSaga(Order order) {
        // Step 1: Fetch cart
        log.info("Step 1: Fetching cart for user {}", order.getUserId());
        CartServiceClient.CartData cart = cartServiceClient.getCartForCheckout(order.getUserId())
            .orElseThrow(() -> new SagaStepFailedException("FETCH_CART", "Cart not found for user: " + order.getUserId()));

        order.setTotalPrice(cart.totalPrice());
        saveOrderEvent(order, "CART_VALIDATED", "Cart fetched with " + cart.items().size() + " items");
        order.setStatus(OrderStatus.CART_VALIDATED);
        orderRepository.save(order);

        // Step 2: Validate products and create order items
        log.info("Step 2: Validating products for order {}", order.getOrderId());
        List<InventoryServiceClient.ReservationData> reservations = new ArrayList<>();
        for (CartServiceClient.CartItemData cartItem : cart.items()) {
            ProductServiceClient.ProductData product = productServiceClient.getProduct(cartItem.productId())
                .orElseThrow(() -> new SagaStepFailedException("VALIDATE_PRODUCT",
                    "Product not found: " + cartItem.productId()));

            OrderItem orderItem = OrderItem.builder()
                .productId(product.id())
                .productName(product.name())
                .quantity(cartItem.quantity())
                .price(product.price())
                .order(order)
                .build();
            order.getItems().add(orderItem);

            // Step 3: Reserve inventory per item
            log.info("Step 3: Reserving inventory for product {} (order {})", product.id(), order.getOrderId());
            InventoryServiceClient.ReservationData reservation = inventoryServiceClient.reserveStock(
                String.valueOf(product.id()), order.getOrderId(), cartItem.quantity())
                .orElseThrow(() -> new SagaStepFailedException("RESERVE_INVENTORY",
                    "Failed to reserve product: " + product.id()));
            reservations.add(reservation);
        }

        orderRepository.save(order);

        saveOrderEvent(order, "INVENTORY_RESERVED", reservations.size() + " items reserved");
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepository.save(order);

        // Step 4: Process payment
        log.info("Step 4: Processing payment for order {} (amount: {})", order.getOrderId(), order.getTotalPrice());
        PaymentServiceClient.PaymentResult payment = paymentServiceClient.processPayment(
            order.getOrderId(), order.getTotalPrice())
            .orElseThrow(() -> new SagaStepFailedException("PROCESS_PAYMENT",
                "Payment service unavailable for order: " + order.getOrderId()));

        if (payment.success()) {
            // Confirm inventory reservations
            for (int i = 0; i < reservations.size(); i++) {
                InventoryServiceClient.ReservationData res = reservations.get(i);
                String productId = cart.items().get(i).productId().toString();
                inventoryServiceClient.confirmReservation(productId, res.reservationId());
            }

            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            saveOrderEvent(order, "ORDER_CONFIRMED", "Payment successful: " + payment.paymentId());
            eventPublisher.publishOrderConfirmed(order);
            log.info("Order {} confirmed successfully", order.getOrderId());
        } else {
            // Compensate: cancel inventory reservations
            log.warn("Payment failed for order {}, compensating", order.getOrderId());
            compensateInventory(reservations, cart.items());
            throw new SagaStepFailedException("PROCESS_PAYMENT", "Payment declined: " + payment.message());
        }

        return toOrderResponse(order);
    }

    private void compensateInventory(List<InventoryServiceClient.ReservationData> reservations,
                                      List<CartServiceClient.CartItemData> items) {
        log.warn("Compensating inventory reservations for failed order");
        for (int i = 0; i < reservations.size(); i++) {
            try {
                String productId = items.get(i).productId().toString();
                inventoryServiceClient.cancelReservation(productId, reservations.get(i).reservationId());
            } catch (Exception e) {
                log.error("Failed to cancel reservation {}: {}", reservations.get(i).reservationId(), e.getMessage());
            }
        }
    }

    public OrderResponse failOrder(Order order, String reason) {
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        saveOrderEvent(order, "ORDER_FAILED", reason);
        eventPublisher.publishOrderFailed(order, reason);
        return toOrderResponse(order);
    }

    private void saveOrderEvent(Order order, String eventType, String details) {
        OrderEvent orderEvent = OrderEvent.builder()
            .orderId(order.getOrderId())
            .eventType(eventType)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
        orderEventRepository.save(orderEvent);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .toList();

        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .userId(order.getUserId())
            .totalPrice(order.getTotalPrice())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .items(itemResponses)
            .build();
    }
}
```

- [ ] **Step 2: Create OrderService.java**

```java
package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEvent;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderSaga orderSaga;

    @Transactional
    public OrderResponse createOrder(Long userId) {
        log.info("Creating order for user: {}", userId);
        return orderSaga.createOrder(userId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED
            || order.getStatus() == OrderStatus.FAILED) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), "cancel");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        saveOrderEvent(order, "ORDER_CANCELLED", "User requested cancellation");
        eventPublisher.publishOrderCancelled(order, "User requested cancellation");

        log.info("Order {} cancelled", orderId);
        return toOrderResponse(order);
    }

    private void saveOrderEvent(Order order, String eventType, String details) {
        OrderEvent orderEvent = OrderEvent.builder()
            .orderId(order.getOrderId())
            .eventType(eventType)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
        orderEventRepository.save(orderEvent);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .toList();

        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .userId(order.getUserId())
            .totalPrice(order.getTotalPrice())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .items(itemResponses)
            .build();
    }
}
```

- [ ] **Step 3: Create OrderController.java**

```java
package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management API")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create order from cart")
    @ApiResponse(responseCode = "201", description = "Order created")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "List user's orders")
    @ApiResponse(responseCode = "200", description = "Orders retrieved")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable Long userId) {
        List<OrderResponse> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order")
    @ApiResponse(responseCode = "200", description = "Order cancelled")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "409", description = "Order cannot be cancelled")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
mvn -pl order-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 10: Cart Service Integration — Clear Cart on Order Created

**Files:**
- Create: `cart-service/src/main/java/com/ecommerce/cart/event/OrderCreatedEventHandler.java`
- Modify: `cart-service/src/main/resources/application.yml`

- [ ] **Step 1: Create OrderCreatedEventHandler.java**

```java
package com.ecommerce.cart.event;

import com.ecommerce.cart.service.CartService;
import com.ecommerce.shared.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedEventHandler {

    private final CartService cartService;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> {
            log.info("Received OrderCreatedEvent for user: {}, order: {}",
                event.getUserId(), event.getOrderId());
            cartService.clearCart(String.valueOf(event.getUserId()));
            log.info("Cart cleared for user: {} after order creation", event.getUserId());
        };
    }
}
```

- [ ] **Step 2: Update cart-service application.yml**

Add to `spring.cloud.stream.bindings`:

```yaml
        orderCreated-in-0:
          destination: order.events
          group: cart-service
```

After the existing bindings in the file.

- [ ] **Step 3: Verify cart-service compiles**

```bash
mvn -pl cart-service -am compile -q
```

Expected: BUILD SUCCESS.

---

### Task 11: API Gateway Route and Docker Compose Updates

**Files:**
- Modify: `api-gateway-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add order-service route to API Gateway**

Edit `api-gateway-service/src/main/resources/application.yml` — add after the `inventory-service` route:

```yaml
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
```

- [ ] **Step 2: Add order-service and payment-service to docker-compose.yml**

Add after the `inventory-service` block:

```yaml
  order-service:
    build:
      context: .
      dockerfile: order-service/Dockerfile
    ports:
      - "8085:8085"
    environment:
      EUREKA_URI: http://eureka-server-service:8761/eureka/
      INSTANCE_HOSTNAME: order-service
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      eureka-server-service:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8085/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - ecommerce-network

  payment-service:
    build:
      context: .
      dockerfile: payment-service/Dockerfile
    ports:
      - "8086:8086"
    environment:
      EUREKA_URI: http://eureka-server-service:8761/eureka/
      INSTANCE_HOSTNAME: payment-service
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      eureka-server-service:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8086/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - ecommerce-network
```

- [ ] **Step 3: Verify full project compiles**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

---

### Task 12: Order Service — Unit Tests

**Files:**
- Create: `order-service/src/test/java/com/ecommerce/order/service/OrderServiceTest.java`
- Create: `order-service/src/test/java/com/ecommerce/order/service/OrderSagaTest.java`

- [ ] **Step 1: Write OrderServiceTest.java**

```java
package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private OrderSaga orderSaga;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderEventRepository, eventPublisher, orderSaga);
    }

    @Test
    void createOrder_ShouldDelegateToSaga() {
        Long userId = 1L;
        OrderResponse expected = OrderResponse.builder()
            .orderId("order-123")
            .userId(userId)
            .status(OrderStatus.PENDING)
            .build();
        when(orderSaga.createOrder(userId)).thenReturn(expected);

        OrderResponse result = orderService.createOrder(userId);

        assertEquals(expected.getOrderId(), result.getOrderId());
        assertEquals(userId, result.getUserId());
        verify(orderSaga).createOrder(userId);
    }

    @Test
    void getOrder_WhenExists_ShouldReturnOrder() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrder(orderId);

        assertEquals(orderId, result.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void getOrder_WhenNotExists_ShouldThrow() {
        when(orderRepository.findByOrderId("nonexistent")).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder("nonexistent"));
    }

    @Test
    void getUserOrders_ShouldReturnList() {
        Long userId = 1L;
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(createTestOrder("order-1", OrderStatus.CONFIRMED)));

        List<OrderResponse> orders = orderService.getUserOrders(userId);

        assertEquals(1, orders.size());
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void cancelOrder_WhenPending_ShouldCancel() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(eventPublisher).publishOrderCancelled(any(), anyString());
    }

    @Test
    void cancelOrder_WhenConfirmed_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void cancelOrder_WhenCancelled_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CANCELLED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void cancelOrder_WhenFailed_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.FAILED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    private Order createTestOrder(String orderId, OrderStatus status) {
        Order order = Order.builder()
            .id(1L)
            .orderId(orderId)
            .userId(1L)
            .totalPrice(new BigDecimal("99.99"))
            .status(status)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .items(new ArrayList<>())
            .build();
        order.getItems().add(OrderItem.builder()
            .id(1L)
            .productId(1L)
            .productName("Test Product")
            .quantity(1)
            .price(new BigDecimal("99.99"))
            .order(order)
            .build());
        return order;
    }
}
```

- [ ] **Step 2: Write OrderSagaTest.java**

```java
package com.ecommerce.order.service;

import com.ecommerce.order.client.CartServiceClient;
import com.ecommerce.order.client.InventoryServiceClient;
import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private CartServiceClient cartServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private InventoryServiceClient inventoryServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    private OrderSaga orderSaga;

    @BeforeEach
    void setUp() {
        orderSaga = new OrderSaga(orderRepository, orderEventRepository, eventPublisher,
            cartServiceClient, productServiceClient, inventoryServiceClient, paymentServiceClient);
    }

    @Test
    void createOrder_HappyPath_ShouldConfirm() {
        Long userId = 1L;
        Order order = createPendingOrder(userId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 2, new BigDecimal("49.99"), new BigDecimal("99.98"))),
            new BigDecimal("99.98"),
            "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("49.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        InventoryServiceClient.ReservationData reservation = new InventoryServiceClient.ReservationData(
            "res-1", "1", 2, "CONFIRMED");
        when(inventoryServiceClient.reserveStock("1", order.getOrderId(), 2))
            .thenReturn(Optional.of(reservation));
        when(inventoryServiceClient.confirmReservation("1", "res-1")).thenReturn(true);

        PaymentServiceClient.PaymentResult payment = new PaymentServiceClient.PaymentResult(
            1L, order.getOrderId(), new BigDecimal("99.98"), true, "Approved");
        when(paymentServiceClient.processPayment(order.getOrderId(), new BigDecimal("99.98")))
            .thenReturn(Optional.of(payment));

        OrderResponse response = orderSaga.createOrder(userId);

        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(eventPublisher).publishOrderConfirmed(any(Order.class));
        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_WhenPaymentFails_ShouldCompensate() {
        Long userId = 1L;
        Order order = createPendingOrder(userId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))),
            new BigDecimal("99.99"),
            "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("99.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        InventoryServiceClient.ReservationData reservation = new InventoryServiceClient.ReservationData(
            "res-1", "1", 1, "CONFIRMED");
        when(inventoryServiceClient.reserveStock("1", order.getOrderId(), 1))
            .thenReturn(Optional.of(reservation));

        PaymentServiceClient.PaymentResult payment = new PaymentServiceClient.PaymentResult(
            1L, order.getOrderId(), new BigDecimal("99.99"), false, "Declined");
        when(paymentServiceClient.processPayment(order.getOrderId(), new BigDecimal("99.99")))
            .thenReturn(Optional.of(payment));

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
        verify(inventoryServiceClient).cancelReservation("1", "res-1");
        verify(eventPublisher).publishOrderFailed(any(Order.class), anyString());
    }

    @Test
    void createOrder_WhenCartFails_ShouldFail() {
        Long userId = 1L;
        Order order = createPendingOrder(userId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.empty());

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
    }

    @Test
    void createOrder_WhenInventoryFails_ShouldFail() {
        Long userId = 1L;
        Order order = createPendingOrder(userId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))),
            new BigDecimal("99.99"),
            "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("99.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        when(inventoryServiceClient.reserveStock("1", order.getOrderId(), 1))
            .thenReturn(Optional.empty());

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
    }

    private Order createPendingOrder(Long userId) {
        Order order = Order.builder()
            .id(1L)
            .orderId(java.util.UUID.randomUUID().toString())
            .userId(userId)
            .totalPrice(BigDecimal.ZERO)
            .status(OrderStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .items(new ArrayList<>())
            .version(0L)
            .build();
        return order;
    }
}
```

- [ ] **Step 3: Run order-service tests**

```bash
mvn -pl order-service test -q
```

Expected: All tests pass.

---

### Task 13: Full Project Build

- [ ] **Step 1: Build the entire project**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS for all modules.

- [ ] **Step 2: Run all tests**

```bash
mvn test -q
```

Expected: All tests pass across all modules.

---

### Task 14: Finish Feature Branch

- [ ] **Step 1: Commit all changes**

```bash
git add -A
git commit -m "feat: add order-service with saga pattern and payment-service"
```

- [ ] **Step 2: Finish feature branch**

```bash
git flow feature finish order-service
git checkout master
git merge develop
```

Expected: Feature merged into develop, branch deleted. On master.
