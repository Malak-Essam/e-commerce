package com.ecommerce.shared.event;

public class InventoryReservationFailedEvent extends DomainEvent {

    private Long productId;
    private Integer requestedQuantity;
    private Integer availableQuantity;

    public InventoryReservationFailedEvent() {
    }

    public InventoryReservationFailedEvent(String aggregateId, Long productId, Integer requestedQuantity, Integer availableQuantity) {
        super(aggregateId);
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(Integer requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
}
