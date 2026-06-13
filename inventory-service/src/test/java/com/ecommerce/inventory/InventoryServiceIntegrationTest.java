package com.ecommerce.inventory;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.shared.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @MockBean
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        Inventory inventory = Inventory.builder()
                .productId("1")
                .totalStock(100)
                .reservedStock(0)
                .build();
        inventoryRepository.save(inventory);
    }

    @Test
    void reserveThenConfirm() {
        ReserveResponse reserveResponse = inventoryService.reserveStock("1", "order-1", 10);
        inventoryService.confirmReservation(reserveResponse.getReservationId());

        InventoryResponse response = inventoryService.checkStock("1");
        assertThat(response.getTotalStock()).isEqualTo(90);
        assertThat(response.getReservedStock()).isEqualTo(0);
        assertThat(response.getAvailableStock()).isEqualTo(90);
    }

    @Test
    void reserveThenCancel() {
        inventoryService.reserveStock("1", "order-1", 20);
        InventoryResponse afterReserve = inventoryService.checkStock("1");
        assertThat(afterReserve.getAvailableStock()).isEqualTo(80);

        var reservation = reservationRepository.findByProductIdAndOrderIdAndStatus(
                "1", "order-1", ReservationStatus.PENDING);
        assertThat(reservation).isPresent();

        inventoryService.cancelReservation(reservation.get().getReservationId());

        InventoryResponse afterCancel = inventoryService.checkStock("1");
        assertThat(afterCancel.getTotalStock()).isEqualTo(100);
        assertThat(afterCancel.getReservedStock()).isEqualTo(0);
        assertThat(afterCancel.getAvailableStock()).isEqualTo(100);
    }

    @Test
    void shouldPreventOverselling() throws InterruptedException {
        int threadCount = 20;
        int quantityPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String orderId = "order-" + i;
            executor.submit(() -> {
                try {
                    inventoryService.reserveStock("1", orderId, quantityPerThread);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        InventoryResponse finalState = inventoryService.checkStock("1");
        int totalReserved = successCount.get() * quantityPerThread;

        assertThat(totalReserved).isLessThanOrEqualTo(100);
        assertThat(finalState.getReservedStock()).isEqualTo(totalReserved);
        assertThat(finalState.getAvailableStock()).isEqualTo(100 - totalReserved);
        assertThat(failureCount.get()).isGreaterThan(0);
    }
}
