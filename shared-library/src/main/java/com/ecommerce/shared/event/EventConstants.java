package com.ecommerce.shared.event;

public final class EventConstants {

    private EventConstants() {
    }

    public static final String ORDER_EXCHANGE = "order.events";
    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String INVENTORY_EXCHANGE = "inventory.events";
    public static final String DLQ_EXCHANGE = "dlq.events";

    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.queue";
    public static final String ORDER_FAILED_QUEUE = "order.failed.queue";
    public static final String PAYMENT_PROCESSED_QUEUE = "payment.processed.queue";
    public static final String INVENTORY_RESERVED_QUEUE = "inventory.reserved.queue";
    public static final String INVENTORY_RESERVATION_FAILED_QUEUE = "inventory.reservation.failed.queue";

    public static final String DLQ_SUFFIX = ".dlq";
}
