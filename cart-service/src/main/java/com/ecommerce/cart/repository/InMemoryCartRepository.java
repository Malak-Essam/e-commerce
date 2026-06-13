package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class InMemoryCartRepository implements CartRepository {

    private static final Duration CART_EXPIRATION = Duration.ofDays(30);

    private final Map<String, Cart> carts = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByUserId(String userId) {
        Cart cart = carts.get(userId);
        if (cart != null && isExpired(cart)) {
            carts.remove(userId);
            log.info("Removed expired cart for user: {}", userId);
            return Optional.empty();
        }
        return Optional.ofNullable(cart);
    }

    @Override
    public Cart save(Cart cart) {
        cart.setLastModifiedAt(Instant.now());
        carts.put(cart.getUserId(), cart);
        return cart;
    }

    @Override
    public void deleteByUserId(String userId) {
        carts.remove(userId);
    }

    @Override
    public void deleteAll() {
        carts.clear();
    }

    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanupExpiredCarts() {
        int before = carts.size();
        carts.values().removeIf(this::isExpired);
        int removed = before - carts.size();
        if (removed > 0) {
            log.info("Scheduled cleanup removed {} expired carts", removed);
        }
    }

    private boolean isExpired(Cart cart) {
        return Duration.between(cart.getLastModifiedAt(), Instant.now()).compareTo(CART_EXPIRATION) > 0;
    }
}
