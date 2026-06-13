package com.ecommerce.cart.event;

import com.ecommerce.cart.service.CartService;
import com.ecommerce.shared.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedEventHandler {

    private final CartService cartService;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> {
            log.info("Received OrderCreatedEvent for user: {}, order: {}",
                event.getUserId(), event.getOrderId());
            cartService.clearCart(String.valueOf(event.getUserId()));
            log.info("Cart cleared for user: {} after order creation", event.getUserId());
        };
    }
}
