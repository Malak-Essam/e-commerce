package com.ecommerce.inventory.event;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.shared.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledEventHandler implements Consumer<OrderCancelledEvent> {

    private final InventoryService inventoryService;

    @Override
    public void accept(OrderCancelledEvent event) {
        log.info("Received order cancelled event for order: {}", event.getOrderId());
        inventoryService.cancelPendingReservationsByOrderId(event.getOrderId());
    }
}
