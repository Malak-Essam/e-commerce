package com.ecommerce.shared.event;

public class OrderFailedEvent extends DomainEvent {

    private Long orderId;
    private String reason;

    public OrderFailedEvent() {
    }

    public OrderFailedEvent(String aggregateId, Long orderId, String reason) {
        super(aggregateId);
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
