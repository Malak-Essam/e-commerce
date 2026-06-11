package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.exception.GlobalExceptionHandler;
import com.ecommerce.product.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private ProductResponse createProductResponse(Long id, String name, String category) {
        return ProductResponse.builder()
                .id(id)
                .name(name)
                .description("Description")
                .price(new BigDecimal("10.00"))
                .stock(5)
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllProducts_ShouldReturn200() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(
                List.of(createProductResponse(1L, "Product A", "Cat1")),
                PageRequest.of(0, 10), 1
        );
        when(productService.getAllProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Product A"));
    }

    @Test
    void getProductById_ShouldReturn200_WhenExists() throws Exception {
        ProductResponse product = createProductResponse(1L, "Test Product", "Cat1");
        when(productService.getProductById(1L)).thenReturn(product);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.category").value("Cat1"));
    }

    @Test
    void getProductById_ShouldReturn404_WhenNotFound() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new EntityNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product not found with id: 99"));
    }

    @Test
    void getProductsByCategory_ShouldReturn200() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(
                List.of(createProductResponse(1L, "Product A", "Electronics")),
                PageRequest.of(0, 10), 1
        );
        when(productService.getProductsByCategory(eq("Electronics"), any())).thenReturn(page);

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Product A"));
    }
}
