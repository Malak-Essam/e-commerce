package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.event.PaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<PaymentResponse> responseCaptor;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(eventPublisher);
    }

    @Test
    void processPayment_ShouldReturnResponse() {
        PaymentRequest request = PaymentRequest.builder()
            .orderId("order-123")
            .amount(new BigDecimal("99.99"))
            .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertNotNull(response.getPaymentId());
        assertEquals("order-123", response.getOrderId());
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        assertNotNull(response.getMessage());

        verify(eventPublisher).publishPaymentProcessed(responseCaptor.capture());
        PaymentResponse captured = responseCaptor.getValue();
        assertEquals(response.getPaymentId(), captured.getPaymentId());
    }
}
