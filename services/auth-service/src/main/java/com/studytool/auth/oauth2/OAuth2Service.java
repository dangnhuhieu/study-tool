package com.studytool.auth.oauth2;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.entity.RefreshToken;
import com.studytool.auth.entity.User;
import com.studytool.auth.exception.OAuthProviderConflictException;
import com.studytool.auth.oauth2.dto.OAuth2UserInfo;
import com.studytool.auth.oauth2.provider.OAuth2Provider;
import com.studytool.auth.repository.RefreshTokenRepository;
import com.studytool.auth.repository.UserRepository;
import com.studytool.auth.security.JwtProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the OAuth2 login flow: authorization URL generation, code exchange,
 * user find-or-create, and JWT issuance.
 */
@Service
public class OAuth2Service {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;
  private final Map<String, OAuth2Provider> providers;

  public OAuth2Service(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtProvider jwtProvider,
      List<OAuth2Provider> providerList) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProvider = jwtProvider;
    this.providers = providerList.stream()
        .collect(Collectors.toMap(
            p -> p.getProviderName().toUpperCase(),
            Function.identity()));
  }

  /**
   * Returns the authorization URL the client should redirect the browser to.
   *
   * @param providerName "google" or "github" (case-insensitive)
   * @throws IllegalArgumentException for unknown providers
   */
  public String getAuthorizationUrl(String providerName) {
    return resolveProvider(providerName).buildAuthorizationUrl();
  }

  /**
   * Handles the OAuth2 callback: exchanges the code, finds or creates the user,
   * and returns JWT tokens.
   *
   * @param providerName "google" or "github" (case-insensitive)
   * @param code         the authorization code from the callback query param
   * @throws IllegalArgumentException        for unknown providers
   * @throws com.studytool.auth.exception.InvalidCredentialsException if the code is invalid
   * @throws OAuthProviderConflictException  if the email is registered with a different provider
   */
  @Transactional
  public AuthResponse handleCallback(String providerName, String code) {
    OAuth2Provider provider = resolveProvider(providerName);
    OAuth2UserInfo userInfo = provider.exchangeCodeForUserInfo(code);
    User user = findOrCreateUser(userInfo, provider.getProviderName());
    return issueTokens(user);
  }

  // --- private helpers ---

  private OAuth2Provider resolveProvider(String providerName) {
    String key = providerName.toUpperCase();
    OAuth2Provider provider = providers.get(key);
    if (provider == null) {
      throw new IllegalArgumentException("Unsupported OAuth2 provider: " + providerName);
    }
    return provider;
  }

  private User findOrCreateUser(OAuth2UserInfo userInfo, String providerName) {
    Optional<User> existing = userRepository.findByEmail(userInfo.email());

    if (existing.isPresent()) {
      User user = existing.get();

      // Email registered with a different provider — reject
      if (!providerName.equalsIgnoreCase(user.getProvider())) {
        throw new OAuthProviderConflictException(userInfo.email(), user.getProvider());
      }

      // Same provider but provider_id not yet stored — update it
      if (user.getProviderId() == null) {
        user.setProviderId(userInfo.id());
        return userRepository.save(user);
      }

      return user;
    }

    // New user — create with OAuth2 provider, no password
    User newUser = User.builder()
        .email(userInfo.email())
        .name(userInfo.name())
        .provider(providerName.toUpperCase())
        .providerId(userInfo.id())
        .role("ROLE_USER")
        .enabled(true)
        .build();
    return userRepository.save(newUser);
  }

  private AuthResponse issueTokens(User user) {
    String accessToken = jwtProvider.generateAccessToken(user);
    String refreshTokenValue = jwtProvider.generateRefreshToken(user);
    long expiresInSeconds = jwtProvider.getAccessExpirationMs() / 1000;

    Instant refreshExpiry = Instant.now().plusMillis(604800000L); // 7 days
    RefreshToken refreshToken = RefreshToken.builder()
        .userId(user.getId())
        .token(refreshTokenValue)
        .expiresAt(refreshExpiry)
        .build();
    refreshTokenRepository.save(refreshToken);

    return new AuthResponse(accessToken, refreshTokenValue, "Bearer", expiresInSeconds);
  }
}
