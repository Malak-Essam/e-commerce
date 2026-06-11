package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product createProduct(Long id, String name, String category) {
        return Product.builder()
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
    void getAllProducts_ShouldReturnPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(
                createProduct(1L, "Product A", "Cat1"),
                createProduct(2L, "Product B", "Cat2")
        );
        Page<Product> productPage = new PageImpl<>(products, pageable, 2);
        when(productRepository.findAll(any(Pageable.class))).thenReturn(productPage);

        Page<ProductResponse> result = productService.getAllProducts(pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Product A");
        assertThat(result.getContent().get(1).getName()).isEqualTo("Product B");
    }

    @Test
    void getProductById_ShouldReturnProduct_WhenExists() {
        Product product = createProduct(1L, "Test Product", "Cat1");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse result = productService.getProductById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Product");
        assertThat(result.getCategory()).isEqualTo("Cat1");
    }

    @Test
    void getProductById_ShouldThrow_WhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getProductsByCategory_ShouldReturnFilteredResults() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(
                createProduct(1L, "Product A", "Electronics"),
                createProduct(3L, "Product C", "Electronics")
        );
        Page<Product> productPage = new PageImpl<>(products, pageable, 2);
        when(productRepository.findByCategory("Electronics", pageable)).thenReturn(productPage);

        Page<ProductResponse> result = productService.getProductsByCategory("Electronics", pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("Electronics");
        assertThat(result.getContent().get(1).getCategory()).isEqualTo("Electronics");
    }

    @Test
    void getProductsByCategory_ShouldReturnEmpty_WhenNoProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByCategory("NonExistent", pageable))
                .thenReturn(Page.empty());

        Page<ProductResponse> result = productService.getProductsByCategory("NonExistent", pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
