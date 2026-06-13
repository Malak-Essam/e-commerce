package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEvent;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderSaga orderSaga;

    @Transactional
    public OrderResponse createOrder(Long userId) {
        log.info("Creating order for user: {}", userId);
        return orderSaga.createOrder(userId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED
            || order.getStatus() == OrderStatus.FAILED) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), "cancel");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        saveOrderEvent(order, "ORDER_CANCELLED", "User requested cancellation");
        eventPublisher.publishOrderCancelled(order, "User requested cancellation");
        log.info("Order {} cancelled", orderId);
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
