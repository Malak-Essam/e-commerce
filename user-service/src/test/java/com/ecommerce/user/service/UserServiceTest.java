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
import com.ecommerce.user.security.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ProductServiceClient productServiceClient;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtUtil, productServiceClient);
    }

    @Test
    void register_ShouldCreateUser_WhenEmailNotTaken() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("encodedPassword");

        User savedUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getId()).isEqualTo(1L);

        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordEncoder).encode("Password1");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("Password1")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ShouldReturnAuthResponse_WhenCredentialsValid() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .build();

        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token-123");

        AuthResponse response = userService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token-123");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void login_ShouldThrow_WhenEmailNotFound() {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("Password1")
                .build();

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_ShouldThrow_WhenPasswordWrong() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("WrongPass1")
                .build();

        User user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", "encodedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void getUserById_ShouldReturnUser_WhenExists() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded")
                .firstName("John")
                .lastName("Doe")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void getUserById_ShouldThrow_WhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void emailExists_ShouldReturnTrue_WhenEmailTaken() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        boolean result = userService.emailExists("taken@example.com");

        assertThat(result).isTrue();
    }

    @Test
    void emailExists_ShouldReturnFalse_WhenEmailFree() {
        when(userRepository.existsByEmail("free@example.com")).thenReturn(false);

        boolean result = userService.emailExists("free@example.com");

        assertThat(result).isFalse();
    }

    @Test
    void getFavoriteProducts_ShouldReturnProducts_WhenServiceIsUp() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded")
                .firstName("John")
                .lastName("Doe")
                .favoriteProductIds(Set.of(1L, 3L))
                .createdAt(LocalDateTime.now())
                .build();

        FavoriteProductResponse product1 = FavoriteProductResponse.builder()
                .id(1L).name("Product 1").price(new BigDecimal("29.99")).stock(10).category("Electronics")
                .build();
        FavoriteProductResponse product3 = FavoriteProductResponse.builder()
                .id(3L).name("Product 3").price(new BigDecimal("15.00")).stock(5).category("Books")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(product1));
        when(productServiceClient.getProduct(3L)).thenReturn(Optional.of(product3));

        FavoriteProductsResponse response = userService.getFavoriteProducts(1L);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getFavorites()).hasSize(2);
        assertThat(response.getSource()).isEqualTo("product-service");
        assertThat(response.getFavorites())
                .extracting(FavoriteProductResponse::getName)
                .containsExactlyInAnyOrder("Product 1", "Product 3");
    }

    @Test
    void getFavoriteProducts_ShouldReturnPartial_WhenSomeFail() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded")
                .firstName("John")
                .lastName("Doe")
                .favoriteProductIds(Set.of(1L, 99L))
                .createdAt(LocalDateTime.now())
                .build();

        FavoriteProductResponse product1 = FavoriteProductResponse.builder()
                .id(1L).name("Product 1").price(new BigDecimal("29.99")).stock(10).category("Electronics")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productServiceClient.getProduct(1L)).thenReturn(Optional.of(product1));
        when(productServiceClient.getProduct(99L)).thenReturn(Optional.empty());

        FavoriteProductsResponse response = userService.getFavoriteProducts(1L);

        assertThat(response).isNotNull();
        assertThat(response.getFavorites()).hasSize(1);
        assertThat(response.getSource()).isEqualTo("fallback");
    }

    @Test
    void getFavoriteProducts_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getFavoriteProducts(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
