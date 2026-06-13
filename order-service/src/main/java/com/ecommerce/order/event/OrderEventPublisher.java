package com.ecommerce.order.event;

import com.ecommerce.order.entity.Order;
import com.ecommerce.shared.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final EventPublisher eventPublisher;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getOrderId(),
            order.getId(),
            order.getUserId(),
            order.getTotalPrice()
        );
        log.info("Publishing OrderCreatedEvent for order: {}", order.getOrderId());
        eventPublisher.publish(event);
    }

    public void publishOrderConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
            order.getOrderId(),
            order.getId(),
            order.getStatus().name()
        );
        log.info("Publishing OrderConfirmedEvent for order: {}", order.getOrderId());
        eventPublisher.publish(event);
    }

    public void publishOrderFailed(Order order, String reason) {
        OrderFailedEvent event = new OrderFailedEvent(
            order.getOrderId(),
            order.getId(),
            reason
        );
        log.info("Publishing OrderFailedEvent for order: {} reason: {}", order.getOrderId(), reason);
        eventPublisher.publish(event);
    }

    public void publishOrderCancelled(Order order, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(
            order.getOrderId(),
            order.getOrderId(),
            reason
        );
        log.info("Publishing OrderCancelledEvent for order: {} reason: {}", order.getOrderId(), reason);
        eventPublisher.publish(event);
    }
}
