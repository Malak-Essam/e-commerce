package com.ecommerce.order.client;

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
                ? new BigDecimal(map.get("totalPrice").toString()) : BigDecimal.ZERO;
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
