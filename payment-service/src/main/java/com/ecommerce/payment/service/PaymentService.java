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
