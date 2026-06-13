package com.ecommerce.shared.event;

public interface EventPublisher {
    void publish(DomainEvent event);
}
