package com.ecommerce.cart.service;

import com.ecommerce.cart.client.ProductServiceClient;
import com.ecommerce.cart.dto.ProductResponse;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.ProductLookupException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productServiceClient);
    }

    @Test
    void addItem_shouldCreateCartAndAddItem() {
        String userId = "user1";
        Long productId = 1L;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(5).category("cat")
                .build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));

        var response = cartService.addItem(userId, productId, 2);

        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals("1", response.getItems().getFirst().getProductId());
        assertEquals(2, response.getItems().getFirst().getQuantity());
        assertEquals(BigDecimal.TEN, response.getItems().getFirst().getPrice());
        assertEquals(new BigDecimal("20.00"), response.getTotalPrice());
    }

    @Test
    void addItem_shouldIncrementQuantityWhenSameProductAddedTwice() {
        String userId = "user1";
        Long productId = 1L;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(5).category("cat")
                .build();

        Cart existingCart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        existingCart.getItems().put("1", CartItem.builder()
                .productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));

        var response = cartService.addItem(userId, productId, 2);

        assertEquals(1, response.getItems().size());
        assertEquals(3, response.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("30.00"), response.getTotalPrice());
    }

    @Test
    void addItem_shouldThrowWhenProductNotFound() {
        String userId = "user1";
        Long productId = 1L;

        when(productServiceClient.getProduct(productId)).thenReturn(Optional.empty());

        assertThrows(ProductLookupException.class, () -> cartService.addItem(userId, productId, 1));
        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    void removeItem_shouldRemoveItemAndRecalculate() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());
        cart.getItems().put("2", CartItem.builder().productId("2").quantity(1).price(new BigDecimal("5.00")).build());
        cart.setTotalPrice(new BigDecimal("25.00"));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.removeItem(userId, 1L);

        assertEquals(1, response.getItems().size());
        assertEquals("2", response.getItems().getFirst().getProductId());
        assertEquals(new BigDecimal("5.00"), response.getTotalPrice());
    }

    @Test
    void removeItem_shouldThrowWhenItemNotFound() {
        String userId = "user1";
        Cart cart = Cart.builder().userId(userId).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(CartItemNotFoundException.class, () -> cartService.removeItem(userId, 1L));
    }

    @Test
    void updateItemQuantity_shouldUpdateAndRecalculate() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.updateItemQuantity(userId, 1L, 5);

        assertEquals(5, response.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("50.00"), response.getTotalPrice());
    }

    @Test
    void updateItemQuantity_shouldRemoveItemWhenQuantityIsZero() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(2).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.updateItemQuantity(userId, 1L, 0);

        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getTotalPrice());
    }

    @Test
    void getCart_shouldReturnCart() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        var response = cartService.getCart(userId);

        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void getCart_shouldThrowWhenNotFound() {
        when(cartRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.getCart("unknown"));
    }

    @Test
    void clearCart_shouldDeleteCart() {
        cartService.clearCart("user1");
        verify(cartRepository).deleteByUserId("user1");
    }

    @Test
    void checkout_shouldReturnCartSnapshot() {
        String userId = "user1";
        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        cart.getItems().put("1", CartItem.builder().productId("1").quantity(1).price(BigDecimal.TEN).build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        var response = cartService.checkout(userId);

        assertNotNull(response);
        assertEquals("user1", response.getUserId());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void checkout_shouldThrowWhenCartEmpty() {
        String userId = "user1";
        Cart cart = Cart.builder().userId(userId).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(IllegalArgumentException.class, () -> cartService.checkout(userId));
    }

    @Test
    void checkout_shouldThrowWhenCartNotFound() {
        when(cartRepository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.checkout("unknown"));
    }

    @Test
    void getOrCreateCart_shouldCreateNewCartWhenNoneExists() {
        String userId = "newUser";
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cartService.getOrCreateCart(userId);

        assertEquals(userId, response.getUserId());
        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, response.getTotalPrice());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void getOrCreateCart_shouldReturnExistingCart() {
        String userId = "existingUser";
        Cart existing = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        var response = cartService.getOrCreateCart(userId);

        assertEquals(userId, response.getUserId());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_shouldThrowOnNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addItem("u1", 1L, -1));
    }

    @Test
    void concurrentAdds_shouldResultInCorrectTotalQuantity() throws InterruptedException {
        String userId = "concurrentUser";
        Long productId = 1L;
        int threadCount = 10;
        ProductResponse product = ProductResponse.builder()
                .id(productId).price(BigDecimal.TEN).name("Test").stock(100).category("cat")
                .build();

        Cart cart = Cart.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(productServiceClient.getProduct(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Exception> exception = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cartService.addItem(userId, productId, 1);
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertNull(exception.get());
        assertEquals(threadCount, cart.getItems().get("1").getQuantity());
        assertEquals(new BigDecimal("100.00"), cart.getTotalPrice());
    }
}
