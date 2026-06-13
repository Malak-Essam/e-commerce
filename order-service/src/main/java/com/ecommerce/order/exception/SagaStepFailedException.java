package com.ecommerce.order.exception;

public class SagaStepFailedException extends RuntimeException {
    private final String step;
    private final String details;

    public SagaStepFailedException(String step, String details) {
        super("Saga step '" + step + "' failed: " + details);
        this.step = step;
        this.details = details;
    }

    public String getStep() { return step; }
    public String getDetails() { return details; }
}
