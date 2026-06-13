package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "OrderCreated"),
    @JsonSubTypes.Type(value = OrderConfirmedEvent.class, name = "OrderConfirmed"),
    @JsonSubTypes.Type(value = OrderFailedEvent.class, name = "OrderFailed"),
    @JsonSubTypes.Type(value = PaymentProcessedEvent.class, name = "PaymentProcessed"),
    @JsonSubTypes.Type(value = InventoryReservedEvent.class, name = "InventoryReserved"),
    @JsonSubTypes.Type(value = InventoryReservationFailedEvent.class, name = "InventoryReservationFailed")
})
public abstract class DomainEvent implements Serializable {

    private String eventId;
    private LocalDateTime timestamp;
    private String aggregateId;

    protected DomainEvent() {
    }

    protected DomainEvent(String aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.aggregateId = aggregateId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }
}
