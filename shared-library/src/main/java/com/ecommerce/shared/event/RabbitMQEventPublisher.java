package com.ecommerce.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQEventPublisher.class);

    private final StreamBridge streamBridge;

    public RabbitMQEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(DomainEvent event) {
        String bindingName = resolveBinding(event);
        log.info("Publishing event: {} [{}] to binding: {}",
            event.getEventId(), event.getClass().getSimpleName(), bindingName);
        streamBridge.send(bindingName, event);
    }

    private String resolveBinding(DomainEvent event) {
        if (event instanceof OrderCreatedEvent) return "orderCreated-out-0";
        if (event instanceof OrderConfirmedEvent) return "orderConfirmed-out-0";
        if (event instanceof OrderFailedEvent) return "orderFailed-out-0";
        if (event instanceof PaymentProcessedEvent) return "paymentProcessed-out-0";
        if (event instanceof InventoryReservedEvent) return "inventoryReserved-out-0";
        if (event instanceof InventoryReservationFailedEvent) return "inventoryReservationFailed-out-0";
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
    }
}
