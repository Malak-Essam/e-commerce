package com.ecommerce.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InventoryConfirmedEvent extends DomainEvent {
    private String productId;
    private String reservationId;
    private String orderId;

    public InventoryConfirmedEvent(String productId, String reservationId, String orderId) {
        super(productId);
        this.productId = productId;
        this.reservationId = reservationId;
        this.orderId = orderId;
    }
}
