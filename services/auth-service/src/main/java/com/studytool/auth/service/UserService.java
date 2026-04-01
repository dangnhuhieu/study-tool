package com.studytool.auth.service;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.dto.LoginRequest;
import com.studytool.auth.dto.MeResponse;
import com.studytool.auth.dto.RegisterRequest;
import com.studytool.auth.entity.RefreshToken;
import com.studytool.auth.entity.User;
import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.exception.UserAlreadyExistsException;
import com.studytool.auth.repository.RefreshTokenRepository;
import com.studytool.auth.repository.UserRepository;
import com.studytool.auth.security.JwtProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtProvider jwtProvider,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProvider = jwtProvider;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new UserAlreadyExistsException(request.email());
    }

    User user = User.builder()
        .email(request.email())
        .password(passwordEncoder.encode(request.password()))
        .name(request.name())
        .provider("LOCAL")
        .role("ROLE_USER")
        .enabled(true)
        .build();

    User saved = userRepository.save(user);
    return issueTokens(saved);
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(InvalidCredentialsException::new);

    if (!user.isEnabled()) {
      throw new InvalidCredentialsException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new InvalidCredentialsException();
    }

    refreshTokenRepository.deleteAllByUserId(user.getId());
    return issueTokens(user);
  }

  @Transactional
  public AuthResponse refreshToken(String rawToken) {
    RefreshToken stored = refreshTokenRepository.findByToken(rawToken)
        .orElseThrow(InvalidCredentialsException::new);

    if (stored.getExpiresAt().isBefore(Instant.now())) {
      refreshTokenRepository.delete(stored);
      throw new InvalidCredentialsException("Refresh token has expired");
    }

    User user = userRepository.findById(stored.getUserId())
        .orElseThrow(InvalidCredentialsException::new);

    refreshTokenRepository.delete(stored);
    return issueTokens(user);
  }

  @Transactional(readOnly = true)
  public MeResponse getMe(String userId) {
    User user = userRepository.findById(UUID.fromString(userId))
        .orElseThrow(InvalidCredentialsException::new);

    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRole(),
        user.getProvider(),
        user.getCreatedAt());
  }

  private AuthResponse issueTokens(User user) {
    String accessToken = jwtProvider.generateAccessToken(user);
    String refreshTokenValue = jwtProvider.generateRefreshToken(user);
    long expiresInSeconds = jwtProvider.getAccessExpirationMs() / 1000;

    Instant refreshExpiry = Instant.now()
        .plusMillis(604800000L); // 7 days

    RefreshToken refreshToken = RefreshToken.builder()
        .userId(user.getId())
        .token(refreshTokenValue)
        .expiresAt(refreshExpiry)
        .build();
    refreshTokenRepository.save(refreshToken);

    return new AuthResponse(accessToken, refreshTokenValue, "Bearer", expiresInSeconds);
  }
}
