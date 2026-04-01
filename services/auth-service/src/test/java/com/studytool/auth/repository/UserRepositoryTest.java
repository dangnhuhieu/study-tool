package com.studytool.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studytool.auth.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository")
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  private User buildUser(String email, String name) {
    return User.builder()
        .email(email)
        .password("hashed-password")
        .name(name)
        .provider("LOCAL")
        .role("ROLE_USER")
        .enabled(true)
        .build();
  }

  @Nested
  @DisplayName("findByEmail")
  class FindByEmail {

    @Test
    @DisplayName("returns user when email exists")
    void findByEmail_existingEmail_returnsUser() {
      userRepository.save(buildUser("alice@example.com", "Alice"));

      Optional<User> result = userRepository.findByEmail("alice@example.com");

      assertThat(result).isPresent();
      assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
      assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("returns empty when email does not exist")
    void findByEmail_unknownEmail_returnsEmpty() {
      Optional<User> result = userRepository.findByEmail("nobody@example.com");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("is case-sensitive for email lookup")
    void findByEmail_differentCase_returnsEmpty() {
      userRepository.save(buildUser("alice@example.com", "Alice"));

      Optional<User> result = userRepository.findByEmail("ALICE@EXAMPLE.COM");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsByEmail")
  class ExistsByEmail {

    @Test
    @DisplayName("returns true when email is already registered")
    void existsByEmail_existingEmail_returnsTrue() {
      userRepository.save(buildUser("bob@example.com", "Bob"));

      assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    @DisplayName("returns false when email is not registered")
    void existsByEmail_unknownEmail_returnsFalse() {
      assertThat(userRepository.existsByEmail("ghost@example.com")).isFalse();
    }
  }

  @Nested
  @DisplayName("email uniqueness constraint")
  class EmailUniqueness {

    @Test
    @DisplayName("throws DataIntegrityViolationException on duplicate email save")
    void save_duplicateEmail_throwsException() {
      userRepository.save(buildUser("carol@example.com", "Carol"));

      assertThatThrownBy(() -> {
        userRepository.save(buildUser("carol@example.com", "Carol Duplicate"));
        userRepository.flush();
      }).isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    @DisplayName("auto-generates a UUID id on save")
    void save_newUser_generatesId() {
      User saved = userRepository.save(buildUser("dave@example.com", "Dave"));

      assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("auto-populates createdAt and updatedAt on save")
    void save_newUser_populatesTimestamps() {
      User saved = userRepository.save(buildUser("eve@example.com", "Eve"));

      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();
    }
  }
}
