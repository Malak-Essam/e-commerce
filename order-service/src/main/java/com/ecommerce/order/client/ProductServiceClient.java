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
