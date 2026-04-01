package com.studytool.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.studytool.auth.entity.RefreshToken;
import com.studytool.auth.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("RefreshTokenRepository")
class RefreshTokenRepositoryTest {

  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserRepository userRepository;

  private User savedUser;

  @BeforeEach
  void setUp() {
    savedUser = userRepository.save(User.builder()
        .email("test@example.com")
        .password("hashed")
        .name("Test")
        .provider("LOCAL")
        .role("ROLE_USER")
        .enabled(true)
        .build());
  }

  private RefreshToken buildToken(UUID userId, String token, Instant expiresAt) {
    return RefreshToken.builder()
        .userId(userId)
        .token(token)
        .expiresAt(expiresAt)
        .build();
  }

  @Nested
  @DisplayName("findByToken")
  class FindByToken {

    @Test
    @DisplayName("returns token when it exists")
    void findByToken_existingToken_returnsToken() {
      refreshTokenRepository.save(
          buildToken(savedUser.getId(), "my-token", Instant.now().plusSeconds(3600)));

      Optional<RefreshToken> result = refreshTokenRepository.findByToken("my-token");

      assertThat(result).isPresent();
      assertThat(result.get().getToken()).isEqualTo("my-token");
      assertThat(result.get().getUserId()).isEqualTo(savedUser.getId());
    }

    @Test
    @DisplayName("returns empty when token does not exist")
    void findByToken_unknownToken_returnsEmpty() {
      Optional<RefreshToken> result = refreshTokenRepository.findByToken("no-such-token");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("deleteAllByUserId")
  class DeleteAllByUserId {

    @Test
    @DisplayName("removes all tokens for a given user")
    void deleteAllByUserId_existingTokens_deletesAll() {
      refreshTokenRepository.save(
          buildToken(savedUser.getId(), "token-1", Instant.now().plusSeconds(3600)));
      refreshTokenRepository.save(
          buildToken(savedUser.getId(), "token-2", Instant.now().plusSeconds(7200)));

      refreshTokenRepository.deleteAllByUserId(savedUser.getId());

      assertThat(refreshTokenRepository.findByToken("token-1")).isEmpty();
      assertThat(refreshTokenRepository.findByToken("token-2")).isEmpty();
    }

    @Test
    @DisplayName("does not affect tokens belonging to other users")
    void deleteAllByUserId_onlyDeletesOwnTokens() {
      User otherUser = userRepository.save(User.builder()
          .email("other@example.com")
          .password("hashed")
          .name("Other")
          .provider("LOCAL")
          .role("ROLE_USER")
          .enabled(true)
          .build());
      refreshTokenRepository.save(
          buildToken(savedUser.getId(), "my-token", Instant.now().plusSeconds(3600)));
      refreshTokenRepository.save(
          buildToken(otherUser.getId(), "other-token", Instant.now().plusSeconds(3600)));

      refreshTokenRepository.deleteAllByUserId(savedUser.getId());

      assertThat(refreshTokenRepository.findByToken("other-token")).isPresent();
    }

    @Test
    @DisplayName("is a no-op when user has no tokens")
    void deleteAllByUserId_noTokens_doesNotThrow() {
      refreshTokenRepository.deleteAllByUserId(UUID.randomUUID());
      // no exception expected
    }
  }

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    @DisplayName("auto-generates UUID id on save")
    void save_newToken_generatesId() {
      RefreshToken saved = refreshTokenRepository.save(
          buildToken(savedUser.getId(), "abc-token", Instant.now().plusSeconds(3600)));

      assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("auto-populates createdAt on save")
    void save_newToken_populatesCreatedAt() {
      RefreshToken saved = refreshTokenRepository.save(
          buildToken(savedUser.getId(), "abc-token", Instant.now().plusSeconds(3600)));

      assertThat(saved.getCreatedAt()).isNotNull();
    }
  }
}
