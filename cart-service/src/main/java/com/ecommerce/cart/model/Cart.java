package com.ecommerce.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    private String userId;
    @Builder.Default
    private Map<String, CartItem> items = new HashMap<>();
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private Instant createdAt;
    private Instant lastModifiedAt;
}
