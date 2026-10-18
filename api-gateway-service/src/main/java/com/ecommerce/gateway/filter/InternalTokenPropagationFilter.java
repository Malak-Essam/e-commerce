package com.ecommerce.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

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
        return 1;
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

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
