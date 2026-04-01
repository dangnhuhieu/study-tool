package com.studytool.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.entity.RefreshToken;
import com.studytool.auth.entity.User;
import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.exception.OAuthProviderConflictException;
import com.studytool.auth.oauth2.dto.OAuth2UserInfo;
import com.studytool.auth.oauth2.provider.OAuth2Provider;
import com.studytool.auth.repository.RefreshTokenRepository;
import com.studytool.auth.repository.UserRepository;
import com.studytool.auth.security.JwtProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2Service")
class OAuth2ServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private OAuth2Provider googleProvider;
  @Mock private OAuth2Provider githubProvider;

  private OAuth2Service oauth2Service;

  @BeforeEach
  void setUp() {
    when(googleProvider.getProviderName()).thenReturn("GOOGLE");
    when(githubProvider.getProviderName()).thenReturn("GITHUB");
    oauth2Service = new OAuth2Service(
        userRepository,
        refreshTokenRepository,
        jwtProvider,
        java.util.List.of(googleProvider, githubProvider));
  }

  @Nested
  @DisplayName("getAuthorizationUrl")
  class GetAuthorizationUrl {

    @Test
    @DisplayName("delegates to google provider for 'google' provider name")
    void getAuthorizationUrl_google_delegatesToGoogleProvider() {
      when(googleProvider.buildAuthorizationUrl()).thenReturn("https://accounts.google.com/auth?...");

      String url = oauth2Service.getAuthorizationUrl("google");

      assertThat(url).isEqualTo("https://accounts.google.com/auth?...");
      verify(googleProvider).buildAuthorizationUrl();
    }

    @Test
    @DisplayName("delegates to github provider for 'github' provider name")
    void getAuthorizationUrl_github_delegatesToGithubProvider() {
      when(githubProvider.buildAuthorizationUrl()).thenReturn("https://github.com/login/oauth/authorize?...");

      String url = oauth2Service.getAuthorizationUrl("github");

      assertThat(url).isEqualTo("https://github.com/login/oauth/authorize?...");
      verify(githubProvider).buildAuthorizationUrl();
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unknown provider")
    void getAuthorizationUrl_unknownProvider_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> oauth2Service.getAuthorizationUrl("facebook"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("facebook");
    }

    @Test
    @DisplayName("is case-insensitive for provider name")
    void getAuthorizationUrl_uppercaseGoogle_delegatesToGoogleProvider() {
      when(googleProvider.buildAuthorizationUrl()).thenReturn("https://accounts.google.com/auth?...");

      String url = oauth2Service.getAuthorizationUrl("GOOGLE");

      assertThat(url).isNotNull();
    }
  }

  @Nested
  @DisplayName("handleCallback")
  class HandleCallback {

    @Test
    @DisplayName("creates new user when email is not registered before")
    void handleCallback_newEmail_createsUserAndReturnsTokens() {
      var userInfo = new OAuth2UserInfo("google-123", "newuser@example.com", "New User");
      when(googleProvider.exchangeCodeForUserInfo("auth-code")).thenReturn(userInfo);
      when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      AuthResponse response = oauth2Service.handleCallback("google", "auth-code");

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      verify(userRepository).save(argThat(u ->
          "GOOGLE".equals(u.getProvider())
              && "google-123".equals(u.getProviderId())
              && "newuser@example.com".equals(u.getEmail())));
    }

    @Test
    @DisplayName("returns tokens for existing OAuth2 user with same provider")
    void handleCallback_existingOAuthUser_returnsTokensWithoutCreatingUser() {
      var userId = UUID.randomUUID();
      var existingUser = User.builder()
          .id(userId)
          .email("existing@example.com")
          .name("Existing")
          .provider("GOOGLE")
          .providerId("google-123")
          .role("ROLE_USER")
          .enabled(true)
          .build();
      var userInfo = new OAuth2UserInfo("google-123", "existing@example.com", "Existing");
      when(googleProvider.exchangeCodeForUserInfo("auth-code")).thenReturn(userInfo);
      when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      AuthResponse response = oauth2Service.handleCallback("google", "auth-code");

      assertThat(response.accessToken()).isEqualTo("access-token");
      // Should not create a new user
      verify(userRepository, never()).save(argThat(u -> u.getId() == null));
    }

    @Test
    @DisplayName("updates provider_id when existing OAuth2 user has null provider_id")
    void handleCallback_existingOAuthUserMissingProviderId_updatesProviderIdAndReturnsTokens() {
      var userId = UUID.randomUUID();
      var existingUser = User.builder()
          .id(userId)
          .email("existing@example.com")
          .name("Existing")
          .provider("GOOGLE")
          .providerId(null)
          .role("ROLE_USER")
          .enabled(true)
          .build();
      var userInfo = new OAuth2UserInfo("google-123", "existing@example.com", "Existing");
      when(googleProvider.exchangeCodeForUserInfo("auth-code")).thenReturn(userInfo);
      when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      oauth2Service.handleCallback("google", "auth-code");

      verify(userRepository).save(argThat(u -> "google-123".equals(u.getProviderId())));
    }

    @Test
    @DisplayName("throws OAuthProviderConflictException when email exists with LOCAL provider")
    void handleCallback_emailExistsWithLocalProvider_throwsOAuthProviderConflictException() {
      var localUser = User.builder()
          .id(UUID.randomUUID())
          .email("local@example.com")
          .name("Local User")
          .provider("LOCAL")
          .role("ROLE_USER")
          .enabled(true)
          .build();
      var userInfo = new OAuth2UserInfo("google-123", "local@example.com", "Local User");
      when(googleProvider.exchangeCodeForUserInfo("auth-code")).thenReturn(userInfo);
      when(userRepository.findByEmail("local@example.com")).thenReturn(Optional.of(localUser));

      assertThatThrownBy(() -> oauth2Service.handleCallback("google", "auth-code"))
          .isInstanceOf(OAuthProviderConflictException.class);

      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws OAuthProviderConflictException when email exists with different OAuth provider")
    void handleCallback_emailExistsWithDifferentOAuthProvider_throwsOAuthProviderConflictException() {
      var githubUser = User.builder()
          .id(UUID.randomUUID())
          .email("user@example.com")
          .name("User")
          .provider("GITHUB")
          .providerId("gh-456")
          .role("ROLE_USER")
          .enabled(true)
          .build();
      var userInfo = new OAuth2UserInfo("google-123", "user@example.com", "User");
      when(googleProvider.exchangeCodeForUserInfo("auth-code")).thenReturn(userInfo);
      when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(githubUser));

      assertThatThrownBy(() -> oauth2Service.handleCallback("google", "auth-code"))
          .isInstanceOf(OAuthProviderConflictException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when code exchange fails")
    void handleCallback_invalidCode_throwsInvalidCredentialsException() {
      when(googleProvider.exchangeCodeForUserInfo("bad-code"))
          .thenThrow(new InvalidCredentialsException("OAuth2 code exchange failed"));

      assertThatThrownBy(() -> oauth2Service.handleCallback("google", "bad-code"))
          .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unknown provider")
    void handleCallback_unknownProvider_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> oauth2Service.handleCallback("twitter", "some-code"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("twitter");
    }

    @Test
    @DisplayName("new user gets ROLE_USER by default")
    void handleCallback_newUser_getsDefaultRole() {
      var userInfo = new OAuth2UserInfo("google-999", "brand-new@example.com", "Brand New");
      when(googleProvider.exchangeCodeForUserInfo("code")).thenReturn(userInfo);
      when(userRepository.findByEmail("brand-new@example.com")).thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("at");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("rt");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      oauth2Service.handleCallback("google", "code");

      verify(userRepository).save(argThat(u -> "ROLE_USER".equals(u.getRole())));
    }

    @Test
    @DisplayName("new user is enabled by default")
    void handleCallback_newUser_isEnabledByDefault() {
      var userInfo = new OAuth2UserInfo("google-777", "enabled@example.com", "Enabled User");
      when(googleProvider.exchangeCodeForUserInfo("code")).thenReturn(userInfo);
      when(userRepository.findByEmail("enabled@example.com")).thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("at");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("rt");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      oauth2Service.handleCallback("google", "code");

      verify(userRepository).save(argThat(User::isEnabled));
    }

    @Test
    @DisplayName("new user has no password set")
    void handleCallback_newUser_hasNoPassword() {
      var userInfo = new OAuth2UserInfo("google-555", "nopassword@example.com", "No Pass");
      when(googleProvider.exchangeCodeForUserInfo("code")).thenReturn(userInfo);
      when(userRepository.findByEmail("nopassword@example.com")).thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("at");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("rt");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      oauth2Service.handleCallback("google", "code");

      verify(userRepository).save(argThat(u -> u.getPassword() == null));
    }
  }
}
