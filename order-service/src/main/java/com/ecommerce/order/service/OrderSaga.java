package com.ecommerce.order.service;

import com.ecommerce.order.client.CartServiceClient;
import com.ecommerce.order.client.InventoryServiceClient;
import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderEvent;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.SagaStepFailedException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSaga {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final CartServiceClient cartServiceClient;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Transactional
    public OrderResponse createOrder(Long userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalPrice(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING);
        order.setItems(new ArrayList<>());
        orderRepository.save(order);

        eventPublisher.publishOrderCreated(order);

        try {
            return executeSaga(order);
        } catch (Exception e) {
            log.error("Saga failed for order {}: {}", order.getOrderId(), e.getMessage());
            return failOrder(order, "Saga failed: " + e.getMessage());
        }
    }

    private OrderResponse executeSaga(Order order) {
        log.info("Step 1: Fetching cart for user {}", order.getUserId());
        CartServiceClient.CartData cart = cartServiceClient.getCartForCheckout(order.getUserId())
            .orElseThrow(() -> new SagaStepFailedException("FETCH_CART", "Cart not found for user: " + order.getUserId()));

        order.setTotalPrice(cart.totalPrice());
        saveOrderEvent(order, "CART_VALIDATED", "Cart fetched with " + cart.items().size() + " items");
        order.setStatus(OrderStatus.CART_VALIDATED);
        orderRepository.save(order);

        log.info("Step 2: Validating products for order {}", order.getOrderId());
        List<InventoryServiceClient.ReservationData> reservations = new ArrayList<>();
        for (CartServiceClient.CartItemData cartItem : cart.items()) {
            ProductServiceClient.ProductData product = productServiceClient.getProduct(cartItem.productId())
                .orElseThrow(() -> new SagaStepFailedException("VALIDATE_PRODUCT",
                    "Product not found: " + cartItem.productId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.id());
            orderItem.setProductName(product.name());
            orderItem.setQuantity(cartItem.quantity());
            orderItem.setPrice(product.price());
            orderItem.setOrder(order);
            order.getItems().add(orderItem);

            log.info("Step 3: Reserving inventory for product {} (order {})", product.id(), order.getOrderId());
            InventoryServiceClient.ReservationData reservation = inventoryServiceClient.reserveStock(
                String.valueOf(product.id()), order.getOrderId(), cartItem.quantity())
                .orElseThrow(() -> new SagaStepFailedException("RESERVE_INVENTORY",
                    "Failed to reserve product: " + product.id()));
            reservations.add(reservation);
        }
        orderRepository.save(order);

        saveOrderEvent(order, "INVENTORY_RESERVED", reservations.size() + " items reserved");
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepository.save(order);

        log.info("Step 4: Processing payment for order {} (amount: {})", order.getOrderId(), order.getTotalPrice());
        PaymentServiceClient.PaymentResult payment = paymentServiceClient.processPayment(
            order.getOrderId(), order.getTotalPrice())
            .orElseThrow(() -> new SagaStepFailedException("PROCESS_PAYMENT",
                "Payment service unavailable for order: " + order.getOrderId()));

        if (payment.success()) {
            for (int i = 0; i < reservations.size(); i++) {
                InventoryServiceClient.ReservationData res = reservations.get(i);
                String productId = cart.items().get(i).productId().toString();
                inventoryServiceClient.confirmReservation(productId, res.reservationId());
            }
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            saveOrderEvent(order, "ORDER_CONFIRMED", "Payment successful: " + payment.paymentId());
            eventPublisher.publishOrderConfirmed(order);
            log.info("Order {} confirmed successfully", order.getOrderId());
        } else {
            log.warn("Payment failed for order {}, compensating", order.getOrderId());
            compensateInventory(reservations, cart.items());
            throw new SagaStepFailedException("PROCESS_PAYMENT", "Payment declined: " + payment.message());
        }

        return toOrderResponse(order);
    }

    private void compensateInventory(List<InventoryServiceClient.ReservationData> reservations,
                                      List<CartServiceClient.CartItemData> items) {
        log.warn("Compensating inventory reservations for failed order");
        for (int i = 0; i < reservations.size(); i++) {
            try {
                String productId = items.get(i).productId().toString();
                inventoryServiceClient.cancelReservation(productId, reservations.get(i).reservationId());
            } catch (Exception e) {
                log.error("Failed to cancel reservation {}: {}", reservations.get(i).reservationId(), e.getMessage());
            }
        }
    }

    public OrderResponse failOrder(Order order, String reason) {
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        saveOrderEvent(order, "ORDER_FAILED", reason);
        eventPublisher.publishOrderFailed(order, reason);
        return toOrderResponse(order);
    }

    private void saveOrderEvent(Order order, String eventType, String details) {
        OrderEvent orderEvent = new OrderEvent();
        orderEvent.setOrderId(order.getOrderId());
        orderEvent.setEventType(eventType);
        orderEvent.setDetails(details);
        orderEvent.setTimestamp(LocalDateTime.now());
        orderEventRepository.save(orderEvent);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .toList();

        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .userId(order.getUserId())
            .totalPrice(order.getTotalPrice())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .items(itemResponses)
            .build();
    }
}
