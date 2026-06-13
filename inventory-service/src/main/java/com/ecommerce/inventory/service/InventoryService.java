package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.*;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.shared.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;

    private static final int MAX_RETRIES = 3;

    @Transactional(readOnly = true)
    public InventoryResponse checkStock(String productId) {
        Inventory inventory = findInventoryOrThrow(productId);
        return mapToResponse(inventory);
    }

    @Transactional(readOnly = true)
    public int getAvailableQuantity(String productId) {
        Inventory inventory = findInventoryOrThrow(productId);
        return inventory.getAvailableStock();
    }

    @Transactional
    public ReserveResponse reserveStock(String productId, String orderId, int quantity) {
        return retryOnLockFailure(productId, () -> {
            Inventory inventory = findInventoryOrThrow(productId);

            if (quantity > inventory.getAvailableStock()) {
                eventPublisher.publish(new InventoryReservationFailedEvent(
                        productId, Long.valueOf(productId), quantity, inventory.getAvailableStock()));
                throw new InsufficientStockException(productId, quantity, inventory.getAvailableStock());
            }

            String reservationId = UUID.randomUUID().toString();

            InventoryReservation reservation = InventoryReservation.builder()
                    .reservationId(reservationId)
                    .productId(productId)
                    .orderId(orderId)
                    .quantity(quantity)
                    .status(ReservationStatus.PENDING)
                    .build();

            inventory.setReservedStock(inventory.getReservedStock() + quantity);

            inventoryRepository.save(inventory);
            reservationRepository.save(reservation);

            eventPublisher.publish(new InventoryReservedEvent(productId, Long.valueOf(productId), quantity));

            log.info("Reserved {} units of product {} for order {} (reservation: {})",
                    quantity, productId, orderId, reservationId);

            return ReserveResponse.builder()
                    .reservationId(reservationId)
                    .productId(productId)
                    .quantity(quantity)
                    .status(ReservationStatus.PENDING.name())
                    .build();
        });
    }

    @Transactional
    public void confirmReservation(String reservationId) {
        InventoryReservation reservation = findReservationOrThrow(reservationId);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new InvalidReservationStatusException(
                    reservationId, reservation.getStatus().name(), ReservationStatus.PENDING.name());
        }

        Inventory inventory = findInventoryOrThrow(reservation.getProductId());

        reservation.setStatus(ReservationStatus.CONFIRMED);
        inventory.setTotalStock(inventory.getTotalStock() - reservation.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        eventPublisher.publish(new InventoryConfirmedEvent(
                reservation.getProductId(), reservation.getProductId(),
                reservation.getReservationId(), reservation.getOrderId()));

        log.info("Confirmed reservation {} for product {} (order: {})",
                reservationId, reservation.getProductId(), reservation.getOrderId());
    }

    @Transactional
    public void cancelReservation(String reservationId) {
        InventoryReservation reservation = findReservationOrThrow(reservationId);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new InvalidReservationStatusException(
                    reservationId, reservation.getStatus().name(), ReservationStatus.PENDING.name());
        }

        Inventory inventory = findInventoryOrThrow(reservation.getProductId());

        reservation.setStatus(ReservationStatus.CANCELLED);
        inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        eventPublisher.publish(new InventoryReservationCancelledEvent(
                reservation.getProductId(), reservation.getProductId(),
                reservation.getReservationId(), reservation.getOrderId()));

        log.info("Cancelled reservation {} for product {} (order: {})",
                reservationId, reservation.getProductId(), reservation.getOrderId());
    }

    @Transactional
    public void cancelPendingReservationsByOrderId(String orderId) {
        reservationRepository.findByOrderId(orderId).stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .forEach(r -> cancelReservation(r.getReservationId()));
    }

    private Inventory findInventoryOrThrow(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private InventoryReservation findReservationOrThrow(String reservationId) {
        return reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .totalStock(inventory.getTotalStock())
                .reservedStock(inventory.getReservedStock())
                .availableStock(inventory.getAvailableStock())
                .build();
    }

    private ReserveResponse retryOnLockFailure(String productId, LockAction action) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute();
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                log.warn("Optimistic lock failure for product {} (attempt {}/{})",
                        productId, attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ReservationConflictException(productId, "Retry interrupted");
                    }
                }
            }
        }
        throw new ReservationConflictException(productId,
                "Failed after " + MAX_RETRIES + " retries due to lock contention");
    }

    @FunctionalInterface
    private interface LockAction {
        ReserveResponse execute();
    }
}
