package com.ecommerce.user.controller;

import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@PathVariable String email) {
        boolean exists = userService.emailExists(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
