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
