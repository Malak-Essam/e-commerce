package com.ecommerce.shared.event;

public class InventoryConfirmedEvent extends DomainEvent {
    private Long productId;
    private String reservationId;
    private String orderId;

    public InventoryConfirmedEvent() {
    }

    public InventoryConfirmedEvent(String aggregateId, Long productId, String reservationId, String orderId) {
        super(aggregateId);
        this.productId = productId;
        this.reservationId = reservationId;
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
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
