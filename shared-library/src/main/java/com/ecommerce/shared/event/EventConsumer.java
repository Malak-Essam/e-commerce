package com.ecommerce.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EventConsumer<T extends DomainEvent> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final Class<T> eventType;

    protected EventConsumer(Class<T> eventType) {
        this.eventType = eventType;
    }

    public abstract void handleEvent(T event);

    public Class<T> getEventType() {
        return eventType;
    }

    protected void logReceived(T event) {
        log.info("Received event: {} [{}] for aggregate: {}",
            event.getEventId(), event.getClass().getSimpleName(), event.getAggregateId());
    }
}
