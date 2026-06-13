package com.ecommerce.cart.service;

import com.ecommerce.cart.client.ProductServiceClient;
import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;

    public CartResponse getOrCreateCart(String userId) {
        return toCartResponse(cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .createdAt(Instant.now())
                            .lastModifiedAt(Instant.now())
                            .build();
                    return cartRepository.save(newCart);
                }));
    }

    public CartResponse addItem(String userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        ProductResponse product = productServiceClient.getProduct(productId)
                .orElseThrow(() -> new ProductLookupException("Product not found: " + productId));

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        Cart newCart = Cart.builder()
                                .userId(userId)
                                .createdAt(Instant.now())
                                .lastModifiedAt(Instant.now())
                                .build();
                        return cartRepository.save(newCart);
                    });

            String pid = String.valueOf(productId);
            CartItem existingItem = cart.getItems().get(pid);
            if (existingItem != null) {
                existingItem.setQuantity(existingItem.getQuantity() + quantity);
            } else {
                CartItem newItem = CartItem.builder()
                        .productId(pid)
                        .quantity(quantity)
                        .price(product.getPrice())
                        .build();
                cart.getItems().put(pid, newItem);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse updateItemQuantity(String userId, Long productId, int quantity) {
        String pid = String.valueOf(productId);

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            CartItem item = cart.getItems().get(pid);
            if (item == null) {
                throw new CartItemNotFoundException("Item not found: " + productId);
            }

            if (quantity <= 0) {
                cart.getItems().remove(pid);
            } else {
                item.setQuantity(quantity);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse removeItem(String userId, Long productId) {
        String pid = String.valueOf(productId);

        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            CartItem removed = cart.getItems().remove(pid);
            if (removed == null) {
                throw new CartItemNotFoundException("Item not found: " + productId);
            }

            recalculateTotal(cart);
            cartRepository.save(cart);
            return toCartResponse(cart);
        }
    }

    public CartResponse getCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));
        return toCartResponse(cart);
    }

    public void clearCart(String userId) {
        cartRepository.deleteByUserId(userId);
    }

    public CartResponse checkout(String userId) {
        synchronized (userId.intern()) {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

            if (cart.getItems().isEmpty()) {
                throw new IllegalArgumentException("Cannot checkout with an empty cart");
            }

            return toCartResponse(cart);
        }
    }

    private void recalculateTotal(Cart cart) {
        BigDecimal total = cart.getItems().values().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total.setScale(2, RoundingMode.HALF_UP));
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : cart.getItems().values()) {
            itemResponses.add(CartItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build());
        }
        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalPrice(cart.getTotalPrice())
                .createdAt(cart.getCreatedAt())
                .lastModifiedAt(cart.getLastModifiedAt())
                .build();
    }
}
