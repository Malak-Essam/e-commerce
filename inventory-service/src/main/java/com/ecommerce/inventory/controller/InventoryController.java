package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory and stock management endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products/{productId}")
    @Operation(summary = "Check stock for a product")
    @ApiResponse(responseCode = "200", description = "Stock details returned")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<InventoryResponse> checkStock(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.checkStock(productId));
    }

    @GetMapping("/products/{productId}/available")
    @Operation(summary = "Get available quantity for a product")
    @ApiResponse(responseCode = "200", description = "Available quantity returned")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Integer> getAvailableQuantity(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getAvailableQuantity(productId));
    }

    @PostMapping("/products/{productId}/reserve")
    @Operation(summary = "Reserve stock for an order")
    @ApiResponse(responseCode = "201", description = "Stock reserved successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient stock or invalid input")
    @ApiResponse(responseCode = "409", description = "Reservation conflict, retry")
    public ResponseEntity<ReserveResponse> reserveStock(
            @PathVariable String productId,
            @Valid @RequestBody ReserveRequest request) {
        ReserveResponse response = inventoryService.reserveStock(
                productId, request.getOrderId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/products/{productId}/confirm")
    @Operation(summary = "Confirm a reservation")
    @ApiResponse(responseCode = "200", description = "Reservation confirmed")
    @ApiResponse(responseCode = "400", description = "Invalid reservation status or product mismatch")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    public ResponseEntity<Void> confirmReservation(
            @PathVariable String productId,
            @Valid @RequestBody ConfirmRequest request) {
        inventoryService.validateReservationBelongsToProduct(request.getReservationId(), productId);
        inventoryService.confirmReservation(request.getReservationId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/products/{productId}/cancel")
    @Operation(summary = "Cancel a reservation")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled")
    @ApiResponse(responseCode = "400", description = "Invalid reservation status or product mismatch")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable String productId,
            @Valid @RequestBody CancelRequest request) {
        inventoryService.validateReservationBelongsToProduct(request.getReservationId(), productId);
        inventoryService.cancelReservation(request.getReservationId());
        return ResponseEntity.ok().build();
    }
}
