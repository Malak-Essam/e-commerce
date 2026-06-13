package com.ecommerce.user.client;

import com.ecommerce.user.dto.FavoriteProductResponse;
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
    public Optional<FavoriteProductResponse> getProduct(Long id) {
        FavoriteProductResponse product = webClientBuilder.build()
                .get()
                .uri("http://product-service/api/products/{id}", id)
                .retrieve()
                .bodyToMono(FavoriteProductResponse.class)
                .block(Duration.ofSeconds(2));
        return Optional.ofNullable(product);
    }

    public Optional<FavoriteProductResponse> getProductFallback(Long id, Throwable t) {
        log.warn("Fallback for product {}: {}", id, t.getMessage());
        return Optional.empty();
    }
}
