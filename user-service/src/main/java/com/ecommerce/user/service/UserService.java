package com.ecommerce.user.service;

import com.ecommerce.user.client.ProductServiceClient;
import com.ecommerce.user.dto.AuthResponse;
import com.ecommerce.user.dto.FavoriteProductResponse;
import com.ecommerce.user.dto.FavoriteProductsResponse;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder externalJwtEncoder;
    private final ProductServiceClient productServiceClient;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        user = userRepository.save(user);
        return toUserResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = externalJwtEncoder.encode(
                JwtEncoderParameters.from(
                    JwtClaimsSet.builder()
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("roles", "ROLE_USER")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                        .issuer("user-service")
                        .build()
                )
            ).getTokenValue();
        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return toUserResponse(user);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public FavoriteProductsResponse getFavoriteProducts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Set<Long> favoriteIds = user.getFavoriteProductIds();
        AtomicBoolean usedFallback = new AtomicBoolean(false);

        List<FavoriteProductResponse> products = favoriteIds.stream()
                .map(id -> {
                    Optional<FavoriteProductResponse> result = productServiceClient.getProduct(id);
                    if (result.isEmpty()) {
                        usedFallback.set(true);
                    }
                    return result;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        String source = usedFallback.get() ? "fallback" : "product-service";
        return FavoriteProductsResponse.builder()
                .userId(userId)
                .favorites(products)
                .source(source)
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
