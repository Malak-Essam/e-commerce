package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.GlobalExceptionHandler;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOrCreateCart_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.getOrCreateCart("user1")).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"));
    }

    @Test
    void addItem_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId(1L).quantity(2).price(BigDecimal.TEN)
                        .subtotal(new BigDecimal("20.00")).build()))
                .totalPrice(new BigDecimal("20.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.addItem(eq("user1"), eq(1L), eq(2))).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.totalPrice").value(20.00));
    }

    @Test
    void addItem_shouldReturn400WhenQuantityInvalid() throws Exception {
        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_shouldReturn503WhenProductLookupFails() throws Exception {
        when(cartService.addItem(eq("user1"), eq(1L), eq(1)))
                .thenThrow(new ProductLookupException("Product not found: 1"));

        mockMvc.perform(post("/api/carts/users/{userId}/items", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void updateItemQuantity_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId(1L).quantity(5).price(BigDecimal.TEN)
                        .subtotal(new BigDecimal("50.00")).build()))
                .totalPrice(new BigDecimal("50.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.updateItemQuantity(eq("user1"), eq(1L), eq(5))).thenReturn(response);

        mockMvc.perform(put("/api/carts/users/{userId}/items/{productId}", "user1", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void removeItem_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.removeItem("user1", 1L)).thenReturn(response);

        mockMvc.perform(delete("/api/carts/users/{userId}/items/{productId}", "user1", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(0));
    }

    @Test
    void removeItem_shouldReturn404WhenItemNotFound() throws Exception {
        when(cartService.removeItem("user1", 999L))
                .thenThrow(new CartItemNotFoundException("Item not found: 999"));

        mockMvc.perform(delete("/api/carts/users/{userId}/items/{productId}", "user1", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCart_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.getCart("user1")).thenReturn(response);

        mockMvc.perform(get("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"));
    }

    @Test
    void getCart_shouldReturn404WhenNotFound() throws Exception {
        when(cartService.getCart("unknown"))
                .thenThrow(new CartNotFoundException("Cart not found for user: unknown"));

        mockMvc.perform(get("/api/carts/users/{userId}", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/carts/users/{userId}", "user1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void checkout_shouldReturn200() throws Exception {
        CartResponse response = CartResponse.builder()
                .userId("user1")
                .items(List.of(CartItemResponse.builder()
                        .productId(1L).quantity(1).price(BigDecimal.TEN)
                        .subtotal(BigDecimal.TEN).build()))
                .totalPrice(BigDecimal.TEN)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(cartService.checkout("user1")).thenReturn(response);

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(10));
    }

    @Test
    void checkout_shouldReturn400WhenCartEmpty() throws Exception {
        when(cartService.checkout("user1"))
                .thenThrow(new IllegalArgumentException("Cannot checkout with an empty cart"));

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "user1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_shouldReturn404WhenCartNotFound() throws Exception {
        when(cartService.checkout("unknown"))
                .thenThrow(new CartNotFoundException("Cart not found for user: unknown"));

        mockMvc.perform(post("/api/carts/users/{userId}/checkout", "unknown"))
                .andExpect(status().isNotFound());
    }
}
