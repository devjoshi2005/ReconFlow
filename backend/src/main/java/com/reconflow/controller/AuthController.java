package com.reconflow.controller;

import com.reconflow.dto.LoginRequest;
import com.reconflow.entity.User;
import com.reconflow.repository.UserRepository;
import com.reconflow.security.JwtService;
import com.reconflow.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        // Rate Limiting to prevent brute force (Capacity: 5, Refill: 0.1 per sec)
        List<Long> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("rate:login:" + request.getEmail()),
                "5", "0.1", "1"
        );
        if (result != null && result.get(0) == 0L) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many login attempts. Please try again later."));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                String token = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId().toString());

                Cookie cookie = new Cookie("accessToken", token);
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                cookie.setMaxAge(24 * 60 * 60); // 24 hours
                // In production with HTTPS, use: cookie.setSecure(true);
                
                response.addCookie(cookie);

                return ResponseEntity.ok(Map.of(
                        "message", "Login successful",
                        "role", user.getRole(),
                        "name", user.getName(),
                        "userId", user.getId()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        String role = SecurityUtils.getCurrentUserRole();
        if (role == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
        return ResponseEntity.ok(Map.of(
                "userId", SecurityUtils.getCurrentUserId(),
                "role", role
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("accessToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Delete cookie
        
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
