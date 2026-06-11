package com.ecommerce.product.config;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SeedDataConfig {

    private final ProductRepository productRepository;

    @Bean
    public CommandLineRunner seedProducts() {
        return args -> {
            if (productRepository.count() == 0) {
                List<Product> products = List.of(
                        Product.builder()
                                .name("Wireless Bluetooth Headphones")
                                .description("Noise-cancelling over-ear headphones with 30-hour battery life")
                                .price(new BigDecimal("79.99"))
                                .stock(150)
                                .category("Electronics")
                                .build(),
                        Product.builder()
                                .name("Organic Cotton T-Shirt")
                                .description("Soft, breathable unisex t-shirt available in multiple colors")
                                .price(new BigDecimal("24.99"))
                                .stock(300)
                                .category("Clothing")
                                .build(),
                        Product.builder()
                                .name("Stainless Steel Water Bottle")
                                .description("Double-walled insulated, keeps drinks cold for 24 hours")
                                .price(new BigDecimal("34.99"))
                                .stock(200)
                                .category("Home & Garden")
                                .build(),
                        Product.builder()
                                .name("Mechanical Keyboard")
                                .description("RGB backlit keyboard with Cherry MX switches")
                                .price(new BigDecimal("149.99"))
                                .stock(75)
                                .category("Electronics")
                                .build(),
                        Product.builder()
                                .name("Yoga Mat")
                                .description("Non-slip, eco-friendly, 6mm thick exercise mat")
                                .price(new BigDecimal("29.99"))
                                .stock(120)
                                .category("Sports")
                                .build(),
                        Product.builder()
                                .name("Leather Messenger Bag")
                                .description("Handcrafted genuine leather bag for laptop and documents")
                                .price(new BigDecimal("89.99"))
                                .stock(50)
                                .category("Accessories")
                                .build(),
                        Product.builder()
                                .name("Running Shoes")
                                .description("Lightweight cushioned running shoes with breathable mesh")
                                .price(new BigDecimal("119.99"))
                                .stock(90)
                                .category("Sports")
                                .build(),
                        Product.builder()
                                .name("Ceramic Coffee Mug Set")
                                .description("Set of 4 handcrafted ceramic mugs, dishwasher safe")
                                .price(new BigDecimal("39.99"))
                                .stock(180)
                                .category("Home & Garden")
                                .build()
                );
                productRepository.saveAll(products);
            }
        };
    }
}
