package com.ecommerce.user.controller;

import com.ecommerce.user.dto.FavoriteProductsResponse;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and favorites API")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Returns a user's profile information")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-email")
    @Operation(summary = "Check email availability", description = "Checks whether an email address is already registered")
    @ApiResponse(responseCode = "200", description = "Returns true if email exists, false otherwise")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        boolean exists = userService.emailExists(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping("/{userId}/favorites")
    @Operation(summary = "Get user favorites", description = "Returns a user's favorite products with details from the product service")
    @ApiResponse(responseCode = "200", description = "Favorites retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<FavoriteProductsResponse> getFavoriteProducts(@PathVariable Long userId) {
        FavoriteProductsResponse response = userService.getFavoriteProducts(userId);
        return ResponseEntity.ok(response);
    }
}
