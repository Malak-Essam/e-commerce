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
