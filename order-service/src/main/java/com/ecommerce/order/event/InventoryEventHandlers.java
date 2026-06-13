package com.ecommerce.order.event;

import com.ecommerce.shared.event.InventoryReservationFailedEvent;
import com.ecommerce.shared.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventHandlers {

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Handling InventoryReservedEvent for product: {}", event.getProductId());
    }

    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.info("Handling InventoryReservationFailedEvent for product: {}", event.getProductId());
    }
}
