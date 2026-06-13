package com.ecommerce.shared.event;

public class InventoryReservedEvent extends DomainEvent {

    private Long productId;
    private Integer quantity;

    public InventoryReservedEvent() {
    }

    public InventoryReservedEvent(String aggregateId, Long productId, Integer quantity) {
        super(aggregateId);
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
