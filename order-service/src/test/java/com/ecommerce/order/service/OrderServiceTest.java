package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private OrderSaga orderSaga;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderEventRepository, eventPublisher, orderSaga);
    }

    @Test
    void createOrder_ShouldDelegateToSaga() {
        Long userId = 1L;
        OrderResponse expected = OrderResponse.builder()
            .orderId("order-123")
            .userId(userId)
            .status(OrderStatus.PENDING)
            .build();
        when(orderSaga.createOrder(userId)).thenReturn(expected);

        OrderResponse result = orderService.createOrder(userId);

        assertEquals(expected.getOrderId(), result.getOrderId());
        assertEquals(userId, result.getUserId());
        verify(orderSaga).createOrder(userId);
    }

    @Test
    void getOrder_WhenExists_ShouldReturnOrder() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrder(orderId);

        assertEquals(orderId, result.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void getOrder_WhenNotExists_ShouldThrow() {
        when(orderRepository.findByOrderId("nonexistent")).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder("nonexistent"));
    }

    @Test
    void getUserOrders_ShouldReturnList() {
        Long userId = 1L;
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(createTestOrder("order-1", OrderStatus.CONFIRMED)));

        List<OrderResponse> orders = orderService.getUserOrders(userId);

        assertEquals(1, orders.size());
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void cancelOrder_WhenPending_ShouldCancel() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(eventPublisher).publishOrderCancelled(any(), anyString());
    }

    @Test
    void cancelOrder_WhenConfirmed_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void cancelOrder_WhenCancelled_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.CANCELLED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void cancelOrder_WhenFailed_ShouldThrow() {
        String orderId = "order-123";
        Order order = createTestOrder(orderId, OrderStatus.FAILED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    private Order createTestOrder(String orderId, OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderId(orderId);
        order.setUserId(1L);
        order.setTotalPrice(new BigDecimal("99.99"));
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setItems(new ArrayList<>());

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProductId(1L);
        item.setProductName("Test Product");
        item.setQuantity(1);
        item.setPrice(new BigDecimal("99.99"));
        item.setOrder(order);
        order.getItems().add(item);

        return order;
    }
}
