package com.ecommerce.user.service;

import com.ecommerce.user.dto.AuthResponse;
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

import java.time.LocalDateTime;
import java.util.Optional;

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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtUtil);
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
}
