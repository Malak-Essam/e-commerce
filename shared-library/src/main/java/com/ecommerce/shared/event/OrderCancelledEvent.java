package com.ecommerce.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderCancelledEvent extends DomainEvent {
    private String orderId;
    private String reason;

    public OrderCancelledEvent(String orderId, String reason) {
        super(orderId);
        this.orderId = orderId;
        this.reason = reason;
    }
}
