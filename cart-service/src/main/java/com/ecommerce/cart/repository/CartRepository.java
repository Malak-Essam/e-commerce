package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;

import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(String userId);

    Cart save(Cart cart);

    void deleteByUserId(String userId);

    void deleteAll();
}
