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
