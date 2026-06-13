package com.ecommerce.shared.event;

public class OrderConfirmedEvent extends DomainEvent {

    private Long orderId;
    private String status;

    public OrderConfirmedEvent() {
    }

    public OrderConfirmedEvent(String aggregateId, Long orderId, String status) {
        super(aggregateId);
        this.orderId = orderId;
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
