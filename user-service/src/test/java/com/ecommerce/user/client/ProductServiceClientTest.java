package com.ecommerce.user.client;

import com.ecommerce.user.dto.FavoriteProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductServiceClientTest {

    @Test
    void fallback_ShouldReturnEmpty() {
        ProductServiceClient client = new ProductServiceClient(null);
        Optional<FavoriteProductResponse> result = client.getProductFallback(1L, new RuntimeException("test"));
        assertThat(result).isEmpty();
    }
}
