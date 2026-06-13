package com.ecommerce.shared.event;

import java.math.BigDecimal;

public class PaymentProcessedEvent extends DomainEvent {

    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private boolean success;

    public PaymentProcessedEvent() {
    }

    public PaymentProcessedEvent(String aggregateId, Long paymentId, Long orderId, BigDecimal amount, boolean success) {
        super(aggregateId);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.success = success;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
