package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByReservationId(String reservationId);
    Optional<InventoryReservation> findByProductIdAndOrderIdAndStatus(String productId, String orderId, ReservationStatus status);
    List<InventoryReservation> findByOrderId(String orderId);
}
