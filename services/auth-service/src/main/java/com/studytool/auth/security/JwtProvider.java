package com.studytool.auth.security;

import com.studytool.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private final SecretKey signingKey;
  private final long accessExpirationMs;
  private final long refreshExpirationMs;

  public JwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long accessExpirationMs,
      @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public String generateAccessToken(User user) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("name", user.getName())
        .claim("role", user.getRole())
        .issuedAt(new Date(now))
        .expiration(new Date(now + accessExpirationMs))
        .signWith(signingKey)
        .compact();
  }

  public String generateRefreshToken(User user) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(user.getId().toString())
        .issuedAt(new Date(now))
        .expiration(new Date(now + refreshExpirationMs))
        .signWith(signingKey)
        .compact();
  }

  public boolean isTokenValid(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public String extractUserId(String token) {
    return parseClaims(token).getSubject();
  }

  public String extractEmail(String token) {
    return parseClaims(token).get("email", String.class);
  }

  public String extractName(String token) {
    return parseClaims(token).get("name", String.class);
  }

  public String extractRole(String token) {
    return parseClaims(token).get("role", String.class);
  }

  public long getAccessExpirationMs() {
    return accessExpirationMs;
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
