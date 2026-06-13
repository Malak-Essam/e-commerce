package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.*;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.shared.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void checkStock_shouldReturnCorrectStockDetails() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(3)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));

        InventoryResponse response = inventoryService.checkStock("1");

        assertThat(response.getProductId()).isEqualTo("1");
        assertThat(response.getTotalStock()).isEqualTo(10);
        assertThat(response.getReservedStock()).isEqualTo(3);
        assertThat(response.getAvailableStock()).isEqualTo(7);
    }

    @Test
    void checkStock_shouldThrowProductNotFoundException() {
        when(inventoryRepository.findByProductId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.checkStock("unknown"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void getAvailableQuantity_shouldReturnCorrectQuantity() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(3)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));

        int quantity = inventoryService.getAvailableQuantity("1");

        assertThat(quantity).isEqualTo(7);
    }

    @Test
    void reserveStock_shouldSucceedAndPublishEvent() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(0)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReserveResponse response = inventoryService.reserveStock("1", "order1", 3);

        assertThat(response.getProductId()).isEqualTo("1");
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getReservationId()).isNotNull();

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        InventoryReservedEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getProductId()).isEqualTo(1L);
        assertThat(event.getQuantity()).isEqualTo(3);
    }

    @Test
    void reserveStock_shouldThrowInsufficientStockExceptionAndPublishFailedEvent() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(8)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserveStock("1", "order1", 5))
                .isInstanceOf(InsufficientStockException.class);

        ArgumentCaptor<InventoryReservationFailedEvent> captor = ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        InventoryReservationFailedEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getProductId()).isEqualTo(1L);
        assertThat(event.getRequestedQuantity()).isEqualTo(5);
        assertThat(event.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void reserveStock_shouldRetryOnOptimisticLockingFailureAndSucceed() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(0)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Lock conflict"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReserveResponse response = inventoryService.reserveStock("1", "order1", 3);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(eventPublisher).publish(any(InventoryReservedEvent.class));
    }

    @Test
    void reserveStock_shouldThrowReservationConflictExceptionAfterRetriesExhausted() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(0)
                .build();
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Lock conflict"));

        assertThatThrownBy(() -> inventoryService.reserveStock("1", "order1", 3))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining("1");
        verify(eventPublisher, never()).publish(any(DomainEvent.class));
    }

    @Test
    void confirmReservation_shouldConfirmPendingReservation() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(3)
                .build();
        InventoryReservation reservation = InventoryReservation.builder()
                .reservationId("res-1")
                .productId("1")
                .orderId("order-1")
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findByReservationId("res-1")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.confirmReservation("res-1");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(inventory.getTotalStock()).isEqualTo(7);
        assertThat(inventory.getReservedStock()).isEqualTo(0);

        ArgumentCaptor<InventoryConfirmedEvent> captor = ArgumentCaptor.forClass(InventoryConfirmedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        InventoryConfirmedEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getProductId()).isEqualTo(1L);
        assertThat(event.getReservationId()).isEqualTo("res-1");
        assertThat(event.getOrderId()).isEqualTo("order-1");
    }

    @Test
    void confirmReservation_shouldThrowWhenNotPending() {
        InventoryReservation reservation = InventoryReservation.builder()
                .reservationId("res-1")
                .status(ReservationStatus.CONFIRMED)
                .build();
        when(reservationRepository.findByReservationId("res-1")).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> inventoryService.confirmReservation("res-1"))
                .isInstanceOf(InvalidReservationStatusException.class)
                .hasMessageContaining("res-1");
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void cancelReservation_shouldCancelPendingReservation() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(3)
                .build();
        InventoryReservation reservation = InventoryReservation.builder()
                .reservationId("res-1")
                .productId("1")
                .orderId("order-1")
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findByReservationId("res-1")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.cancelReservation("res-1");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inventory.getReservedStock()).isEqualTo(0);

        ArgumentCaptor<InventoryReservationCancelledEvent> captor = ArgumentCaptor.forClass(InventoryReservationCancelledEvent.class);
        verify(eventPublisher).publish(captor.capture());
        InventoryReservationCancelledEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getProductId()).isEqualTo(1L);
        assertThat(event.getReservationId()).isEqualTo("res-1");
        assertThat(event.getOrderId()).isEqualTo("order-1");
    }

    @Test
    void cancelReservation_shouldThrowWhenNotPending() {
        InventoryReservation reservation = InventoryReservation.builder()
                .reservationId("res-1")
                .status(ReservationStatus.CANCELLED)
                .build();
        when(reservationRepository.findByReservationId("res-1")).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> inventoryService.cancelReservation("res-1"))
                .isInstanceOf(InvalidReservationStatusException.class)
                .hasMessageContaining("res-1");
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void cancelPendingReservationsByOrderId_shouldCancelAllPending() {
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(10)
                .reservedStock(5)
                .build();
        InventoryReservation pending1 = InventoryReservation.builder()
                .reservationId("res-1")
                .productId("1")
                .orderId("order-1")
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .build();
        InventoryReservation pending2 = InventoryReservation.builder()
                .reservationId("res-2")
                .productId("1")
                .orderId("order-1")
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findByOrderId("order-1")).thenReturn(List.of(pending1, pending2));
        when(reservationRepository.findByReservationId("res-1")).thenReturn(Optional.of(pending1));
        when(reservationRepository.findByReservationId("res-2")).thenReturn(Optional.of(pending2));
        when(inventoryRepository.findByProductId("1")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.cancelPendingReservationsByOrderId("order-1");

        assertThat(pending1.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(pending2.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inventory.getReservedStock()).isEqualTo(0);
        verify(eventPublisher, times(2)).publish(any(InventoryReservationCancelledEvent.class));
    }
}
