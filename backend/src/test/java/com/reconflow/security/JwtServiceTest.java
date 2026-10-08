package com.reconflow.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
    }

    @Test
    void testGenerateTokenAndExtractClaims() {
        String username = "analyst@reconflow.com";
        String role = "ROLE_ANALYST";
        String userId = UUID.randomUUID().toString();

        String token = jwtService.generateToken(username, role, userId);
        
        assertNotNull(token);
        assertEquals(username, jwtService.extractUsername(token));
        assertEquals(role, jwtService.extractRole(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, username));
    }

    @Test
    void testTokenValidationFailsForDifferentUser() {
        String token = jwtService.generateToken("admin@reconflow.com", "ROLE_ADMIN", UUID.randomUUID().toString());
        assertFalse(jwtService.isTokenValid(token, "hacker@reconflow.com"));
    }

    @Test
    void testExpiredTokenThrowsException() {
        // Generate an expired token manually using the same key
        Map<String, Object> claims = new HashMap<>();
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject("user@reconflow.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 48)) // 48 hours ago
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)) // Expired 24 hours ago
                .signWith(Keys.hmacShaKeyFor("MockSecureKeyForReconFlowAppWithAtLeast256BitsToSupportHS256Algorithm".getBytes()), SignatureAlgorithm.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
    }

    @Test
    void testForgedSignatureThrowsException() {
        // Token signed with a different key (imitation attempt)
        Map<String, Object> claims = new HashMap<>();
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject("admin@reconflow.com")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                .signWith(Keys.hmacShaKeyFor("HackerSecureKeyForReconFlowAppWithAtLeast256BitsToSupportHS256Algorithm".getBytes()), SignatureAlgorithm.HS256)
                .compact();

        assertThrows(SignatureException.class, () -> jwtService.extractUsername(token));
    }

    @Test
    void testMalformedTokenThrowsException() {
        String token = "this.is.not.a.real.token";
        assertThrows(MalformedJwtException.class, () -> jwtService.extractUsername(token));
    }
}
