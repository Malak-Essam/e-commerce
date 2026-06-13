package com.ecommerce.shared.event;

public class InventoryConfirmedEvent extends DomainEvent {
    private String productId;
    private String reservationId;
    private String orderId;

    public InventoryConfirmedEvent() {
    }

    public InventoryConfirmedEvent(String aggregateId, String productId, String reservationId, String orderId) {
        super(aggregateId);
        this.productId = productId;
        this.reservationId = reservationId;
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
