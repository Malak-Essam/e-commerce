package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateItemRequest;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Shopping cart API")
public class CartController {

    private final CartService cartService;

    @PostMapping("/users/{userId}")
    @Operation(summary = "Get or create cart for user")
    @ApiResponse(responseCode = "200", description = "Cart retrieved or created")
    public ResponseEntity<CartResponse> getOrCreateCart(@PathVariable String userId) {
        CartResponse cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/users/{userId}/items")
    @Operation(summary = "Add item to cart")
    @ApiResponse(responseCode = "200", description = "Item added to cart")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable String userId,
            @Valid @RequestBody AddItemRequest request) {
        CartResponse cart = cartService.addItem(userId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @PutMapping("/users/{userId}/items/{productId}")
    @Operation(summary = "Update item quantity in cart")
    @ApiResponse(responseCode = "200", description = "Item quantity updated")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String userId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateItemRequest request) {
        CartResponse cart = cartService.updateItemQuantity(userId, productId, request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/users/{userId}/items/{productId}")
    @Operation(summary = "Remove item from cart")
    @ApiResponse(responseCode = "200", description = "Item removed")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String userId,
            @PathVariable Long productId) {
        CartResponse cart = cartService.removeItem(userId, productId);
        return ResponseEntity.ok(cart);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get cart details")
    @ApiResponse(responseCode = "200", description = "Cart details retrieved")
    public ResponseEntity<CartResponse> getCart(@PathVariable String userId) {
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Clear cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared")
    public ResponseEntity<Void> clearCart(@PathVariable String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/checkout")
    @Operation(summary = "Prepare cart for checkout")
    @ApiResponse(responseCode = "200", description = "Cart ready for checkout")
    public ResponseEntity<CartResponse> checkout(@PathVariable String userId) {
        CartResponse cart = cartService.checkout(userId);
        return ResponseEntity.ok(cart);
    }
}
