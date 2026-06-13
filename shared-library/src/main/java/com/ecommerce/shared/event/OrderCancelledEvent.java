package com.ecommerce.shared.event;

public class OrderCancelledEvent extends DomainEvent {
    private String orderId;
    private String reason;

    public OrderCancelledEvent() {
    }

    public OrderCancelledEvent(String aggregateId, String orderId, String reason) {
        super(aggregateId);
        this.orderId = orderId;
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
