package com.ecommerce.user.controller;

import com.ecommerce.user.dto.FavoriteProductResponse;
import com.ecommerce.user.dto.FavoriteProductsResponse;
import com.ecommerce.user.exception.GlobalExceptionHandler;
import com.ecommerce.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getFavoriteProducts_ShouldReturn200_WhenUserExists() throws Exception {
        FavoriteProductResponse product = FavoriteProductResponse.builder()
                .id(1L)
                .name("Product 1")
                .price(new BigDecimal("29.99"))
                .stock(10)
                .category("Electronics")
                .build();

        FavoriteProductsResponse response = FavoriteProductsResponse.builder()
                .userId(1L)
                .favorites(List.of(product))
                .source("product-service")
                .build();

        when(userService.getFavoriteProducts(1L)).thenReturn(response);

        mockMvc.perform(get("/api/users/1/favorites")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.favorites[0].name").value("Product 1"))
                .andExpect(jsonPath("$.favorites[0].price").value(29.99))
                .andExpect(jsonPath("$.source").value("product-service"));
    }

    @Test
    void getFavoriteProducts_ShouldReturn404_WhenUserNotFound() throws Exception {
        when(userService.getFavoriteProducts(99L))
                .thenThrow(new EntityNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/99/favorites")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: 99"));
    }
}
