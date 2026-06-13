package com.ecommerce.payment.event;

import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.shared.event.EventPublisher;
import com.ecommerce.shared.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
