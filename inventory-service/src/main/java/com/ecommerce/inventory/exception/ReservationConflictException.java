package com.ecommerce.inventory.exception;

public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String productId, String message) {
        super("Reservation conflict for product " + productId + ": " + message);
    }
}
