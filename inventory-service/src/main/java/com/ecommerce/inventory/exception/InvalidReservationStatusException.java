package com.ecommerce.inventory.exception;

public class InvalidReservationStatusException extends RuntimeException {
    public InvalidReservationStatusException(String reservationId, String currentStatus, String expectedStatus) {
        super("Reservation " + reservationId + " has status " + currentStatus + ", expected " + expectedStatus);
    }
}
