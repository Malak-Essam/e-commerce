package com.ecommerce.order.exception;

import com.ecommerce.order.entity.enums.OrderStatus;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String orderId, OrderStatus current, String action) {
        super(String.format("Order %s is in %s state, cannot %s", orderId, current, action));
    }
}
