# SEC-001-V2: OAuth2 Resource Server Architecture

## Problem

Current SEC-001 has three architectural flaws:
1. **Triple JWT parsing** — gateway, user-service, and shared-library all parse the same token with `JwtUtil`
2. **Shared symmetric secret** — `JwtConfig.secret` is copied verbatim into `application.yml` of both gateway and user-service
3. **Spoofable headers** — gateway strips the JWT and sends `X-User-Id`/`X-User-Email`/`X-User-Roles` as plaintext headers that any host on the internal network could forge

## Solution

Adopt a standard OAuth2 Resource Server pattern with a **two-token chain**:

```
Client                          API Gateway                       Downstream Service
  │                                │                                    │
  │  Authorization: Bearer <ext>   │                                    │
  ├───────────────────────────────►│                                    │
  │                                │  Validate via OAuth2 RS            │
  │                                │  (NimbusJwtDecoder + public key)   │
  │                                │                                    │
  │                                │  Create internal JWT (30s TTL)     │
  │                                │  Signed with gateway's private key │
  │                                │                                    │
  │                                │  X-Internal-Token: <int>           │
  │                                ├───────────────────────────────────►│
  │                                │                                    │
  │                                │               Validate via OAuth2 RS
  │                                │               (NimbusJwtDecoder + internal public key)
  │                                │               Extract Authentication from SecurityContext
```

### Key properties

| Layer | Signer | Validator | Key |
|-------|--------|-----------|-----|
| External JWT | User service (login) | API Gateway | RSA key pair A |
| Internal JWT | API Gateway | All downstream services | RSA key pair B |

## Phases

---

## Phase 0: Key Infrastructure

Generate two RSA key pairs for development:

```bash
# External token key pair (user-service signs, gateway validates)
openssl genrsa -out external-private.pem 2048
openssl rsa -in external-private.pem -pubout -out external-public.pem

# Internal token key pair (gateway signs, microservices validate)
openssl genrsa -out internal-private.pem 2048
openssl rsa -in internal-private.pem -pubout -out internal-public.pem
```

Base64-encode the PEM contents for `application.yml`:

```
jwt:
  external:
    public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
    private-key: MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
  internal:
    public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
    private-key: MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
```

---

## Phase 1: API Gateway — OAuth2 Resource Server + Internal Token Issuer

### 1a. pom.xml

```xml
<!-- api-gateway-service/pom.xml -->
<dependencies>
    <!-- Existing: spring-cloud-starter-gateway, eureka-client, actuator, stream, rabbit -->
    <!-- Keep shared-library for events/DTOs only (remove security classes later) -->
    <dependency>
        <groupId>com.ecommerce</groupId>
        <artifactId>shared-library</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- NEW: OAuth2 Resource Server (brings NimbusJwtDecoder, ReactiveJwtDecoder,
         NimbusJwtEncoder, ServerHttpSecurity.oauth2ResourceServer(), etc.) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>
```

### 1b. application.yml additions

```yaml
# api-gateway-service/src/main/resources/application.yml
jwt:
  external:
    public-key: ${JWT_EXTERNAL_PUBLIC_KEY}
  internal:
    private-key: ${JWT_INTERNAL_PRIVATE_KEY}
    public-key: ${JWT_INTERNAL_PUBLIC_KEY}
    expiration-seconds: 30
```

### 1c. GatewaySecurityConfig.java (replaces GatewayConfig.java)

```java
package com.ecommerce.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${jwt.external.public-key}")
    private String externalPublicKeyPem;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers("/api/users/auth/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
            );
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        RSAPublicKey publicKey = parsePublicKey(externalPublicKeyPem);
        return NimbusReactiveJwtDecoder.withPublicKey(publicKey)
            .jwsAlgorithm(JWSAlgorithm.RS256)
            .build();
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }
}
```

### 1d. InternalTokenPropagationFilter.java (replaces JwtAuthGatewayFilter)

```java
package com.ecommerce.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
@Component
public class InternalTokenPropagationFilter implements GlobalFilter, Ordered {

    private final RSAPrivateKey internalPrivateKey;
    private final RSAPublicKey internalPublicKey;
    private final long expirationSeconds;

    public InternalTokenPropagationFilter(
            @Value("${jwt.internal.private-key}") String privateKeyPem,
            @Value("${jwt.internal.public-key}") String publicKeyPem,
            @Value("${jwt.internal.expiration-seconds}") long expirationSeconds) throws Exception {
        this.internalPrivateKey = parsePrivateKey(privateKeyPem);
        this.internalPublicKey = parsePublicKey(publicKeyPem);
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                Authentication auth = ctx.getAuthentication();
                if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                    return chain.filter(exchange);
                }

                Jwt externalJwt = jwtAuth.getToken();
                String internalToken = createInternalToken(externalJwt);

                ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> h.set("X-Internal-Token", internalToken)))
                    .build();
                return chain.filter(mutated);
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    private String createInternalToken(Jwt externalJwt) {
        try {
            RSAKey rsaKey = new RSAKey.Builder(internalPublicKey)
                .privateKey(internalPrivateKey)
                .keyID("internal-gw")
                .build();

            RSASSASigner signer = new RSASSASigner(rsaKey);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(externalJwt.getSubject())
                .claim("email", externalJwt.getClaimAsString("email"))
                .claim("roles", externalJwt.getClaimAsString("roles"))
                .issuer("api-gateway")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(expirationSeconds)))
                .build();

            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("internal-gw").build(),
                claims
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create internal token", e);
        }
    }

    @Override
    public int getOrder() {
        return 1; // After OAuth2 resource server auth sets SecurityContext
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception { /* same as above */ }
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception { /* PKCS8 decode */ }
}
```

### 1e. Deletions

- Remove `GatewayConfig.java` (no more `JwtUtil` bean)
- Remove `JwtAuthGatewayFilter.java` (replaced by OAuth2 RS + InternalTokenPropagationFilter)
- Remove `jwt.secret` and `jwt.expiration-ms` from `application.yml`

---

## Phase 2: Downstream Services — OAuth2 Resource Servers

Each downstream service (cart, product, order, inventory, payment) gets the same treatment.

### 2a. pom.xml (example: cart-service)

```xml
<!-- cart-service/pom.xml -->
<dependencies>
    <!-- Existing: spring-boot-starter-web, validation, actuator, eureka-client,
         webflux, circuitbreaker, stream, rabbit, shared-library, springdoc, test -->

    <!-- NEW: OAuth2 Resource Server -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>
```

### 2b. application.yml additions (cart-service)

```yaml
# cart-service/src/main/resources/application.yml
jwt:
  internal:
    public-key: ${JWT_INTERNAL_PUBLIC_KEY}
```

### 2c. ResourceServerSecurityConfig.java (one per downstream service)

```java
package com.ecommerce.cart.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebSecurity
public class ResourceServerSecurityConfig {

    @Value("${jwt.internal.public-key}")
    private String internalPublicKeyPem;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(internalTokenResolver())
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
            );
        return http.build();
    }

    @Bean
    public BearerTokenResolver internalTokenResolver() {
        return new BearerTokenResolver() {
            @Override
            public String resolve(HttpServletRequest request) {
                String token = request.getHeader("X-Internal-Token");
                if (token != null && !token.isEmpty()) {
                    return token;
                }
                return null;
            }
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        RSAPublicKey publicKey = parsePublicKey(internalPublicKeyPem);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }
}
```

### 2d. How controllers extract identity

Instead of `UserContext` request attribute, controllers use Spring Security:

```java
@RestController
@RequestMapping("/api/carts")
public class CartController {

    @GetMapping
    public ResponseEntity<List<CartItem>> getCart() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();  // JWT subject = user ID
        // ...
    }
}
```

Or via injection:

```java
@GetMapping
public ResponseEntity<List<CartItem>> getCart(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    String email = jwt.getClaimAsString("email");
    // ...
}
```

### 2e. Deletions per service

- Remove `FilterRegistrationBean<UserContextFilter>` from each service's `@Configuration` class
- Remove the `import com.ecommerce.shared.filter.UserContextFilter;` line

---

## Phase 3: User Service — OAuth2 Resource Server + External Token Issuer

### 3a. pom.xml

```xml
<!-- user-service/pom.xml -->
<dependencies>
    <!-- REMOVE: -->
    <!-- <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency> -->

    <!-- Keep existing: spring-boot-starter-web, data-jpa, security, validation,
         actuator, eureka-client, circuitbreaker, stream, rabbit, shared-library,
         springdoc, test, spring-security-test -->

    <!-- ADD: OAuth2 Resource Server (for internal token validation) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>
```

### 3b. UserServiceSecurityConfig.java (replaces SecurityConfig.java)

```java
package com.ecommerce.user.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebSecurity
public class UserServiceSecurityConfig {

    // External key pair (for signing/validating user tokens)
    @Value("${jwt.external.private-key}")
    private String externalPrivateKeyPem;
    @Value("${jwt.external.public-key}")
    private String externalPublicKeyPem;

    // Internal public key (for validating gateway-issued tokens)
    @Value("${jwt.internal.public-key}")
    private String internalPublicKeyPem;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/users/auth/**", "/h2-console/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            // Validate internal token from gateway for non-auth endpoints
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(internalTokenResolver())
                .jwt(jwt -> jwt.decoder(internalJwtDecoder()))
            );
        return http.build();
    }

    @Bean
    public JwtEncoder externalJwtEncoder() throws Exception {
        RSAPublicKey publicKey = parsePublicKey(externalPublicKeyPem);
        RSAPrivateKey privateKey = parsePrivateKey(externalPrivateKeyPem);
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID("external-key")
            .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder internalJwtDecoder() throws Exception {
        RSAPublicKey publicKey = parsePublicKey(internalPublicKeyPem);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean
    public BearerTokenResolver internalTokenResolver() {
        return request -> {
            String token = request.getHeader("X-Internal-Token");
            if (token != null && !token.isEmpty()) {
                return token;
            }
            return null;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception { /* same as above */ }
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception { /* PKCS8 decode */ }
}
```

### 3c. AuthController — external token generation change

Replace `JwtUtil.generateToken()` with `JwtEncoder`:

```java
private final JwtEncoder externalJwtEncoder;

public AuthResponse login(LoginRequest request) {
    // authenticate...
    String token = externalJwtEncoder.encode(
        JwtEncoderParameters.from(
            JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("roles", "ROLE_USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .issuer("user-service")
                .build()
        )
    ).getTokenValue();
    return new AuthResponse(token, /* ... */);
}
```

### 3d. Deletions

- Remove `SecurityConfig.java` (replaced by `UserServiceSecurityConfig`)
- Remove `JwtAuthFilter.java` (replaced by OAuth2 Resource Server)
- Remove `jwt.secret` and `jwt.expiration-ms` from `application.yml`

---

## Phase 4: Shared Library Cleanup

### 4a. pom.xml

```xml
<!-- shared-library/pom.xml -->
<dependencies>
    <!-- REMOVE: -->
    <!-- <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
    </dependency> -->

    <!-- Keep: jackson-databind, jackson-datatype-jsr310, jakarta.validation-api,
         spring-cloud-stream, spring-boot-starter-logging, spring-boot-starter-test -->
</dependencies>
```

### 4b. Source deletions

Remove these files from `shared-library/src/main/java`:

| File | Reason |
|------|--------|
| `com/ecommerce/shared/security/JwtUtil.java` | Replaced by OAuth2 RS + Nimbus |
| `com/ecommerce/shared/security/UserContext.java` | Replaced by `Authentication` from SecurityContext |
| `com/ecommerce/shared/config/JwtConfig.java` | Replaced by `@Value` per-service config |
| `com/ecommerce/shared/filter/UserContextFilter.java` | Replaced by OAuth2 RS |

Also remove `shared-library/src/test/java/com/ecommerce/shared/security/JwtUtilTest.java`.

---

## Phase 5: Root POM Cleanup

Remove the `jjwt.version` property from `pom.xml` root:

```xml
<!-- Remove: -->
<!-- <jjwt.version>0.12.6</jjwt.version> -->
```

---

## Rollout Order

```
Phase 0  (Key Gen) ─► Phase 1  (Gateway) ─► Phase 3  (User Service)
                          │                        │
                          ▼                        ▼
                     Phase 2 (Cart/Product/    Phase 4 (Shared Library Cleanup)
                              Order/Inventory/      │
                              Payment)              ▼
                                               Phase 5 (Root POM Cleanup)
```

**Recommended execution order** to avoid broken builds:
1. Phase 0 — generate keys
2. Phase 4 — remove `jjwt` + `jakarta.servlet-api` from shared-library, delete security classes (this will break other modules)
3. Phase 1 — fix gateway with OAuth2 RS
4. Phase 3 — fix user-service with OAuth2 RS + `JwtEncoder`
5. Phase 2 — fix all downstream services with OAuth2 RS
6. Phase 5 — clean root POM
7. Verify: `mvn clean compile` passes across all modules
8. Verify: `mvn test` passes
9. Integration test: login → get token → call gateway → route to downstream → verify identity propagation
