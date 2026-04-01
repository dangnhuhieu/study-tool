package com.studytool.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.dto.LoginRequest;
import com.studytool.auth.dto.RegisterRequest;
import com.studytool.auth.entity.RefreshToken;
import com.studytool.auth.entity.User;
import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.exception.UserAlreadyExistsException;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtProvider jwtProvider;

  private PasswordEncoder passwordEncoder;
  private UserService userService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder(10);
    userService = new UserService(userRepository, refreshTokenRepository, jwtProvider, passwordEncoder);
  }

  @Nested
  @DisplayName("register")
  class Register {

    @Test
    @DisplayName("returns AuthResponse with tokens on successful registration")
    void register_validRequest_returnsAuthResponse() {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      AuthResponse response = userService.register(request);

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(86400L);
    }

    @Test
    @DisplayName("hashes password before saving user")
    void register_validRequest_savesHashedPassword() {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      userService.register(request);

      verify(userRepository).save(argThat(user ->
          user.getPassword() != null
              && !user.getPassword().equals("password123")
              && passwordEncoder.matches("password123", user.getPassword())
      ));
    }

    @Test
    @DisplayName("sets provider to LOCAL and role to ROLE_USER by default")
    void register_validRequest_setsDefaultProviderAndRole() {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("t");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("r");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      userService.register(request);

      verify(userRepository).save(argThat(user ->
          "LOCAL".equals(user.getProvider()) && "ROLE_USER".equals(user.getRole())
      ));
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when email is already taken")
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

      assertThatThrownBy(() -> userService.register(request))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasMessageContaining("alice@example.com");

      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("saves a refresh token to the database during registration")
    void register_validRequest_savesRefreshToken() {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      userService.register(request);

      verify(refreshTokenRepository).save(argThat(rt ->
          "refresh-token".equals(rt.getToken()) && rt.getExpiresAt() != null
      ));
    }
  }

  @Nested
  @DisplayName("login")
  class Login {

    @Test
    @DisplayName("returns AuthResponse with tokens on valid credentials")
    void login_validCredentials_returnsAuthResponse() {
      String hashed = passwordEncoder.encode("secret123");
      var user = User.builder()
          .id(UUID.randomUUID())
          .email("bob@example.com")
          .password(hashed)
          .name("Bob")
          .role("ROLE_USER")
          .provider("LOCAL")
          .enabled(true)
          .build();
      var request = new LoginRequest("bob@example.com", "secret123");
      when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      AuthResponse response = userService.login(request);

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when email not found")
    void login_unknownEmail_throwsInvalidCredentialsException() {
      when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.login(new LoginRequest("nobody@example.com", "pw")))
          .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when password is wrong")
    void login_wrongPassword_throwsInvalidCredentialsException() {
      String hashed = passwordEncoder.encode("correct-password");
      var user = User.builder()
          .id(UUID.randomUUID())
          .email("bob@example.com")
          .password(hashed)
          .name("Bob")
          .role("ROLE_USER")
          .provider("LOCAL")
          .enabled(true)
          .build();
      when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> userService.login(new LoginRequest("bob@example.com", "wrong-password")))
          .isInstanceOf(InvalidCredentialsException.class);

      verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when user is disabled")
    void login_disabledUser_throwsInvalidCredentialsException() {
      String hashed = passwordEncoder.encode("secret123");
      var user = User.builder()
          .id(UUID.randomUUID())
          .email("bob@example.com")
          .password(hashed)
          .name("Bob")
          .role("ROLE_USER")
          .provider("LOCAL")
          .enabled(false)
          .build();
      when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> userService.login(new LoginRequest("bob@example.com", "secret123")))
          .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("deletes existing refresh tokens before creating a new one on login")
    void login_validCredentials_replacesExistingRefreshTokens() {
      String hashed = passwordEncoder.encode("secret123");
      var user = User.builder()
          .id(UUID.randomUUID())
          .email("bob@example.com")
          .password(hashed)
          .name("Bob")
          .role("ROLE_USER")
          .provider("LOCAL")
          .enabled(true)
          .build();
      var request = new LoginRequest("bob@example.com", "secret123");
      when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      userService.login(request);

      verify(refreshTokenRepository).deleteAllByUserId(user.getId());
    }
  }

  @Nested
  @DisplayName("refreshToken")
  class RefreshTokenMethod {

    @Test
    @DisplayName("returns new tokens when refresh token is valid and not expired")
    void refreshToken_validToken_returnsNewTokens() {
      var userId = UUID.randomUUID();
      var user = User.builder()
          .id(userId)
          .email("carol@example.com")
          .name("Carol")
          .role("ROLE_USER")
          .provider("LOCAL")
          .enabled(true)
          .build();
      var storedToken = RefreshToken.builder()
          .id(UUID.randomUUID())
          .userId(userId)
          .token("valid-refresh-token")
          .expiresAt(Instant.now().plusSeconds(3600))
          .build();

      when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(storedToken));
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jwtProvider.generateAccessToken(any())).thenReturn("new-access-token");
      when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh-token");
      when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

      AuthResponse response = userService.refreshToken("valid-refresh-token");

      assertThat(response.accessToken()).isEqualTo("new-access-token");
      assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when refresh token not found")
    void refreshToken_unknownToken_throwsInvalidCredentialsException() {
      when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.refreshToken("unknown-token"))
          .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when refresh token is expired")
    void refreshToken_expiredToken_throwsInvalidCredentialsException() {
      var storedToken = RefreshToken.builder()
          .id(UUID.randomUUID())
          .userId(UUID.randomUUID())
          .token("expired-token")
          .expiresAt(Instant.now().minusSeconds(3600))
          .build();
      when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(storedToken));

      assertThatThrownBy(() -> userService.refreshToken("expired-token"))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }
}
