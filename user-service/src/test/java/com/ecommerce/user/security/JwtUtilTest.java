package com.ecommerce.user.security;

import com.ecommerce.user.config.JwtConfig;
import com.ecommerce.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtConfig jwtConfig;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D635166546A576E5A7234753778214125442A47");
        jwtConfig.setExpirationMs(86400000L);
        jwtUtil = new JwtUtil(jwtConfig);
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        User user = User.builder().id(1L).email("test@example.com").build();

        String token = jwtUtil.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(1L);
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenInvalid() {
        boolean valid = jwtUtil.validateToken("invalid-token");
        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenExpired() throws Exception {
        // Token with 0ms expiration will be expired immediately
        jwtConfig.setExpirationMs(0L);
        jwtUtil = new JwtUtil(jwtConfig);

        User user = User.builder().id(1L).email("test@example.com").build();
        String token = jwtUtil.generateToken(user);

        Thread.sleep(10);
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }
}
