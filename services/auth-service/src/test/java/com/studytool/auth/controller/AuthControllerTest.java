package com.studytool.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.dto.LoginRequest;
import com.studytool.auth.dto.MeResponse;
import com.studytool.auth.dto.RefreshTokenRequest;
import com.studytool.auth.dto.RegisterRequest;
import com.studytool.auth.exception.AuthExceptionHandler;
import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.exception.UserAlreadyExistsException;
import com.studytool.shared.exception.GlobalExceptionHandler;
import com.studytool.auth.security.JwtAuthenticationFilter;
import com.studytool.auth.security.JwtProvider;
import com.studytool.auth.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({JwtAuthenticationFilter.class, AuthExceptionHandler.class,
    GlobalExceptionHandler.class, AuthControllerTest.TestSecurityConfig.class})
@DisplayName("AuthController")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserService userService;
  @MockBean private JwtProvider jwtProvider;

  @org.springframework.boot.test.context.TestConfiguration
  static class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(
        HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
      return http
          .csrf(AbstractHttpConfigurer::disable)
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .exceptionHandling(e -> e
              .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
          .authorizeHttpRequests(auth -> auth
              .requestMatchers(
                  "/api/auth/register",
                  "/api/auth/login",
                  "/api/auth/refresh")
              .permitAll()
              .anyRequest().authenticated())
          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
          .build();
    }
  }

  private static final AuthResponse SAMPLE_AUTH_RESPONSE = new AuthResponse(
      "access-token", "refresh-token", "Bearer", 86400L);

  @Nested
  @DisplayName("POST /api/auth/register")
  class Register {

    @Test
    @DisplayName("returns 201 Created with tokens on valid registration")
    void register_validRequest_returns201WithTokens() throws Exception {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userService.register(any())).thenReturn(SAMPLE_AUTH_RESPONSE);

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.data.expiresIn").value(86400));
    }

    @Test
    @DisplayName("returns 409 Conflict when email already exists")
    void register_duplicateEmail_returns409() throws Exception {
      var request = new RegisterRequest("alice@example.com", "password123", "Alice");
      when(userService.register(any()))
          .thenThrow(new UserAlreadyExistsException("alice@example.com"));

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when email is blank")
    void register_blankEmail_returns400() throws Exception {
      var request = new RegisterRequest("", "password123", "Alice");

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when email format is invalid")
    void register_invalidEmailFormat_returns400() throws Exception {
      var request = new RegisterRequest("not-an-email", "password123", "Alice");

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when password is shorter than 8 characters")
    void register_shortPassword_returns400() throws Exception {
      var request = new RegisterRequest("alice@example.com", "short", "Alice");

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when name is blank")
    void register_blankName_returns400() throws Exception {
      var request = new RegisterRequest("alice@example.com", "password123", "");

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("POST /api/auth/login")
  class Login {

    @Test
    @DisplayName("returns 200 OK with tokens on valid credentials")
    void login_validCredentials_returns200WithTokens() throws Exception {
      var request = new LoginRequest("bob@example.com", "secret123");
      when(userService.login(any())).thenReturn(SAMPLE_AUTH_RESPONSE);

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"))
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("returns 401 Unauthorized on invalid credentials")
    void login_invalidCredentials_returns401() throws Exception {
      var request = new LoginRequest("bob@example.com", "wrong");
      when(userService.login(any())).thenThrow(new InvalidCredentialsException());

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when email is blank")
    void login_blankEmail_returns400() throws Exception {
      var request = new LoginRequest("", "secret123");

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when password is blank")
    void login_blankPassword_returns400() throws Exception {
      var request = new LoginRequest("bob@example.com", "");

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("POST /api/auth/refresh")
  class Refresh {

    @Test
    @DisplayName("returns 200 OK with new tokens when refresh token is valid")
    void refresh_validToken_returns200WithNewTokens() throws Exception {
      var request = new RefreshTokenRequest("valid-refresh-token");
      when(userService.refreshToken("valid-refresh-token")).thenReturn(SAMPLE_AUTH_RESPONSE);

      mockMvc.perform(post("/api/auth/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("returns 401 Unauthorized when refresh token is invalid or expired")
    void refresh_invalidToken_returns401() throws Exception {
      var request = new RefreshTokenRequest("expired-token");
      when(userService.refreshToken("expired-token"))
          .thenThrow(new InvalidCredentialsException());

      mockMvc.perform(post("/api/auth/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("returns 400 Bad Request when refresh token field is blank")
    void refresh_blankToken_returns400() throws Exception {
      var request = new RefreshTokenRequest("");

      mockMvc.perform(post("/api/auth/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("GET /api/auth/me")
  class Me {

    @Test
    @DisplayName("returns 200 OK with user info when JWT is valid")
    void me_validJwt_returns200WithUserInfo() throws Exception {
      var meResponse = new MeResponse(
          UUID.fromString("11111111-1111-1111-1111-111111111111"),
          "alice@example.com",
          "Alice",
          "ROLE_USER",
          "LOCAL",
          Instant.parse("2024-01-01T00:00:00Z"));

      when(jwtProvider.isTokenValid("valid-token")).thenReturn(true);
      when(jwtProvider.extractUserId("valid-token"))
          .thenReturn("11111111-1111-1111-1111-111111111111");
      when(userService.getMe("11111111-1111-1111-1111-111111111111")).thenReturn(meResponse);

      mockMvc.perform(get("/api/auth/me")
              .header("Authorization", "Bearer valid-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.email").value("alice@example.com"))
          .andExpect(jsonPath("$.data.name").value("Alice"))
          .andExpect(jsonPath("$.data.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("returns 401 Unauthorized when no Authorization header present")
    void me_noAuthHeader_returns401() throws Exception {
      mockMvc.perform(get("/api/auth/me"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("returns 401 Unauthorized when JWT is invalid")
    void me_invalidJwt_returns401() throws Exception {
      when(jwtProvider.isTokenValid("bad-token")).thenReturn(false);

      mockMvc.perform(get("/api/auth/me")
              .header("Authorization", "Bearer bad-token"))
          .andExpect(status().isUnauthorized());
    }
  }
}
