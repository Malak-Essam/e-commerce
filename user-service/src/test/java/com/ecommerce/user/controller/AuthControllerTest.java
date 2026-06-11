package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AuthResponse;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.exception.GlobalExceptionHandler;
import com.ecommerce.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .build();
    }

    @Test
    void register_ShouldReturn201_WhenValidRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("Password1")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .email("new@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.register(any(RegisterRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/users/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void register_ShouldReturn400_WhenEmailInvalid() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("not-an-email")
                .password("Password1")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/users/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_ShouldReturn400_WhenPasswordTooShort() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Ab1")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/users/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ShouldReturn200_WhenCredentialsValid() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password1")
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .token("jwt-token")
                .user(UserResponse.builder().id(1L).email("test@example.com").build())
                .build();

        when(userService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void login_ShouldReturn400_WhenEmailMissing() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .password("Password1")
                .build();

        mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
