package com.ecommerce.user.security;

import com.ecommerce.user.config.JwtConfig;
import com.ecommerce.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }
}
