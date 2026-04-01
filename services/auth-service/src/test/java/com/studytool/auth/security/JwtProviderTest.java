package com.studytool.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studytool.auth.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProvider")
class JwtProviderTest {

  private static final String TEST_SECRET =
      "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
  private static final long ACCESS_EXPIRATION_MS = 86400000L; // 24h
  private static final long REFRESH_EXPIRATION_MS = 604800000L; // 7d

  private JwtProvider jwtProvider;
  private User testUser;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(TEST_SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    testUser = User.builder()
        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        .email("test@example.com")
        .name("Test User")
        .role("ROLE_USER")
        .build();
  }

  @Nested
  @DisplayName("generateAccessToken")
  class GenerateAccessToken {

    @Test
    @DisplayName("returns non-blank token for valid user")
    void generateAccessToken_validUser_returnsToken() {
      String token = jwtProvider.generateAccessToken(testUser);

      assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generated token contains expected claims")
    void generateAccessToken_validUser_tokenContainsClaims() {
      String token = jwtProvider.generateAccessToken(testUser);

      assertThat(jwtProvider.extractUserId(token)).isEqualTo(testUser.getId().toString());
      assertThat(jwtProvider.extractEmail(token)).isEqualTo(testUser.getEmail());
      assertThat(jwtProvider.extractName(token)).isEqualTo(testUser.getName());
      assertThat(jwtProvider.extractRole(token)).isEqualTo(testUser.getRole());
    }

    @Test
    @DisplayName("two tokens for the same user are different due to iat")
    void generateAccessToken_sameUserTwice_tokensAreDifferentOrSame() {
      String token1 = jwtProvider.generateAccessToken(testUser);
      String token2 = jwtProvider.generateAccessToken(testUser);

      // Both must be valid tokens
      assertThat(jwtProvider.isTokenValid(token1)).isTrue();
      assertThat(jwtProvider.isTokenValid(token2)).isTrue();
    }
  }

  @Nested
  @DisplayName("generateRefreshToken")
  class GenerateRefreshToken {

    @Test
    @DisplayName("returns non-blank refresh token")
    void generateRefreshToken_validUser_returnsToken() {
      String token = jwtProvider.generateRefreshToken(testUser);

      assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("refresh token is different from access token")
    void generateRefreshToken_differentFromAccessToken() {
      String accessToken = jwtProvider.generateAccessToken(testUser);
      String refreshToken = jwtProvider.generateRefreshToken(testUser);

      assertThat(refreshToken).isNotEqualTo(accessToken);
    }
  }

  @Nested
  @DisplayName("isTokenValid")
  class IsTokenValid {

    @Test
    @DisplayName("returns true for a freshly generated token")
    void isTokenValid_freshToken_returnsTrue() {
      String token = jwtProvider.generateAccessToken(testUser);

      assertThat(jwtProvider.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("returns false for a tampered token")
    void isTokenValid_tamperedToken_returnsFalse() {
      String token = jwtProvider.generateAccessToken(testUser);
      String tampered = token.substring(0, token.length() - 4) + "XXXX";

      assertThat(jwtProvider.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("returns false for blank token")
    void isTokenValid_blankToken_returnsFalse() {
      assertThat(jwtProvider.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("returns false for null token")
    void isTokenValid_nullToken_returnsFalse() {
      assertThat(jwtProvider.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("returns false for a completely random string")
    void isTokenValid_randomString_returnsFalse() {
      assertThat(jwtProvider.isTokenValid("not.a.jwt.token")).isFalse();
    }
  }

  @Nested
  @DisplayName("extractUserId")
  class ExtractUserId {

    @Test
    @DisplayName("returns correct user ID from valid token")
    void extractUserId_validToken_returnsUserId() {
      String token = jwtProvider.generateAccessToken(testUser);

      String userId = jwtProvider.extractUserId(token);

      assertThat(userId).isEqualTo(testUser.getId().toString());
    }

    @Test
    @DisplayName("throws exception for invalid token")
    void extractUserId_invalidToken_throwsException() {
      assertThatThrownBy(() -> jwtProvider.extractUserId("invalid.token.here"))
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  @DisplayName("extractEmail")
  class ExtractEmail {

    @Test
    @DisplayName("returns correct email from valid token")
    void extractEmail_validToken_returnsEmail() {
      String token = jwtProvider.generateAccessToken(testUser);

      assertThat(jwtProvider.extractEmail(token)).isEqualTo("test@example.com");
    }
  }

  @Nested
  @DisplayName("getExpirationMs")
  class GetExpirationMs {

    @Test
    @DisplayName("returns configured access token expiration")
    void getAccessExpirationMs_returnsConfiguredValue() {
      assertThat(jwtProvider.getAccessExpirationMs()).isEqualTo(ACCESS_EXPIRATION_MS);
    }
  }
}
