package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.exception.GlobalExceptionHandler;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void checkStock_shouldReturn200() throws Exception {
        InventoryResponse response = InventoryResponse.builder()
                .productId("prod-1")
                .totalStock(100)
                .reservedStock(10)
                .availableStock(90)
                .build();

        when(inventoryService.checkStock("prod-1")).thenReturn(response);

        mockMvc.perform(get("/api/inventory/products/{productId}", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.totalStock").value(100))
                .andExpect(jsonPath("$.reservedStock").value(10))
                .andExpect(jsonPath("$.availableStock").value(90));
    }

    @Test
    void getAvailableQuantity_shouldReturn200() throws Exception {
        when(inventoryService.getAvailableQuantity("prod-1")).thenReturn(90);

        mockMvc.perform(get("/api/inventory/products/{productId}/available", "prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(90));
    }

    @Test
    void reserveStock_shouldReturn201() throws Exception {
        ReserveResponse response = ReserveResponse.builder()
                .reservationId("res-1")
                .productId("prod-1")
                .quantity(5)
                .status("PENDING")
                .build();

        when(inventoryService.reserveStock(eq("prod-1"), eq("order-1"), eq(5)))
                .thenReturn(response);

        mockMvc.perform(post("/api/inventory/products/{productId}/reserve", "prod-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"order-1\", \"quantity\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("res-1"))
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void confirmReservation_shouldReturn200() throws Exception {
        doNothing().when(inventoryService).confirmReservation("res-1");

        mockMvc.perform(post("/api/inventory/products/{productId}/confirm", "prod-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": \"res-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelReservation_shouldReturn200() throws Exception {
        doNothing().when(inventoryService).cancelReservation("res-1");

        mockMvc.perform(post("/api/inventory/products/{productId}/cancel", "prod-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": \"res-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void checkStock_shouldReturn404WhenProductNotFound() throws Exception {
        when(inventoryService.checkStock("unknown"))
                .thenThrow(new ProductNotFoundException("unknown"));

        mockMvc.perform(get("/api/inventory/products/{productId}", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserveStock_shouldReturn400WhenInsufficientStock() throws Exception {
        when(inventoryService.reserveStock(eq("prod-1"), eq("order-1"), eq(999)))
                .thenThrow(new InsufficientStockException("prod-1", 999, 5));

        mockMvc.perform(post("/api/inventory/products/{productId}/reserve", "prod-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"order-1\", \"quantity\": 999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveStock_shouldReturn400WhenValidationFails() throws Exception {
        mockMvc.perform(post("/api/inventory/products/{productId}/reserve", "prod-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest());
    }
}
