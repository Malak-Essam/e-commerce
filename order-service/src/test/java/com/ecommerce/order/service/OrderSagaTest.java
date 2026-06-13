package com.ecommerce.order.service;

import com.ecommerce.order.client.CartServiceClient;
import com.ecommerce.order.client.InventoryServiceClient;
import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.repository.OrderEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private CartServiceClient cartServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private InventoryServiceClient inventoryServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    private OrderSaga orderSaga;

    @BeforeEach
    void setUp() {
        orderSaga = new OrderSaga(orderRepository, orderEventRepository, eventPublisher,
            cartServiceClient, productServiceClient, inventoryServiceClient, paymentServiceClient);
    }

    private Order createPendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderId(UUID.randomUUID().toString());
        order.setUserId(1L);
        order.setTotalPrice(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setVersion(0L);
        return order;
    }

    @Test
    void createOrder_HappyPath_ShouldConfirm() {
        Long userId = 1L;

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId(UUID.randomUUID().toString());
            if (o.getCreatedAt() == null) o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 2, new BigDecimal("49.99"), new BigDecimal("99.98"))),
            new BigDecimal("99.98"), "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("49.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        InventoryServiceClient.ReservationData reservation = new InventoryServiceClient.ReservationData(
            "res-1", "1", 2, "CONFIRMED");
        when(inventoryServiceClient.reserveStock(eq("1"), anyString(), eq(2)))
            .thenReturn(Optional.of(reservation));
        when(inventoryServiceClient.confirmReservation(eq("1"), eq("res-1"))).thenReturn(true);

        PaymentServiceClient.PaymentResult payment = new PaymentServiceClient.PaymentResult(
            1L, "any-order-id", new BigDecimal("99.98"), true, "Approved");
        when(paymentServiceClient.processPayment(anyString(), eq(new BigDecimal("99.98"))))
            .thenReturn(Optional.of(payment));

        OrderResponse response = orderSaga.createOrder(userId);

        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(eventPublisher).publishOrderConfirmed(any(Order.class));
        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_WhenPaymentFails_ShouldFail() {
        Long userId = 1L;

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId(UUID.randomUUID().toString());
            if (o.getCreatedAt() == null) o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))),
            new BigDecimal("99.99"), "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("99.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        InventoryServiceClient.ReservationData reservation = new InventoryServiceClient.ReservationData(
            "res-1", "1", 1, "CONFIRMED");
        when(inventoryServiceClient.reserveStock(eq("1"), anyString(), eq(1)))
            .thenReturn(Optional.of(reservation));

        PaymentServiceClient.PaymentResult payment = new PaymentServiceClient.PaymentResult(
            1L, "any-order-id", new BigDecimal("99.99"), false, "Declined");
        when(paymentServiceClient.processPayment(anyString(), eq(new BigDecimal("99.99"))))
            .thenReturn(Optional.of(payment));

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
        verify(inventoryServiceClient).cancelReservation(eq("1"), eq("res-1"));
        verify(eventPublisher).publishOrderFailed(any(Order.class), anyString());
    }

    @Test
    void createOrder_WhenCartFails_ShouldFail() {
        Long userId = 1L;

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId(UUID.randomUUID().toString());
            return o;
        });

        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.empty());

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_WhenInventoryFails_ShouldFail() {
        Long userId = 1L;

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId(UUID.randomUUID().toString());
            if (o.getCreatedAt() == null) o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))),
            new BigDecimal("99.99"), "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("99.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        when(inventoryServiceClient.reserveStock(eq("1"), anyString(), eq(1)))
            .thenReturn(Optional.empty());

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
    }

    @Test
    void createOrder_WhenPaymentServiceUnavailable_ShouldFail() {
        Long userId = 1L;

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId(UUID.randomUUID().toString());
            if (o.getCreatedAt() == null) o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        CartServiceClient.CartData cartData = new CartServiceClient.CartData(
            List.of(new CartServiceClient.CartItemData(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))),
            new BigDecimal("99.99"), "1"
        );
        when(cartServiceClient.getCartForCheckout(userId)).thenReturn(Optional.of(cartData));

        ProductServiceClient.ProductData productData = new ProductServiceClient.ProductData(
            1L, "Test Product", new BigDecimal("99.99"));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(productData));

        InventoryServiceClient.ReservationData reservation = new InventoryServiceClient.ReservationData(
            "res-1", "1", 1, "CONFIRMED");
        when(inventoryServiceClient.reserveStock(eq("1"), anyString(), eq(1)))
            .thenReturn(Optional.of(reservation));

        when(paymentServiceClient.processPayment(anyString(), any()))
            .thenReturn(Optional.empty());

        OrderResponse response = orderSaga.createOrder(userId);

        assertEquals(OrderStatus.FAILED, response.getStatus());
    }
}
