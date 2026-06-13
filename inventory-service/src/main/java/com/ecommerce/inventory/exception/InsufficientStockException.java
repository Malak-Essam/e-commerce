package com.ecommerce.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    private final int requested;
    private final int available;

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock for product " + productId + ". Requested: " + requested + ", Available: " + available);
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
