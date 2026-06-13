package com.ecommerce.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private String userId;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;
    private Instant createdAt;
    private Instant lastModifiedAt;
}
