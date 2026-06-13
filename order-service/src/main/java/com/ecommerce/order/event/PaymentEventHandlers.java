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
