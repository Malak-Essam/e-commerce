package com.ecommerce.user.config;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("Seeding default user with favorites");

        User user = User.builder()
                .email("demo@example.com")
                .password(passwordEncoder.encode("Demo1234"))
                .firstName("Demo")
                .lastName("User")
                .favoriteProductIds(Set.of(1L, 3L, 5L))
                .build();

        userRepository.save(user);
        log.info("Seeded user ID {} with favorite products [1, 3, 5]", user.getId());
    }
}
