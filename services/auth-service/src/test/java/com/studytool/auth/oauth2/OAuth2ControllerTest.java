package com.studytool.auth.oauth2;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.exception.AuthExceptionHandler;
import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.exception.OAuthProviderConflictException;
import com.studytool.auth.security.JwtAuthenticationFilter;
import com.studytool.auth.security.JwtProvider;
import com.studytool.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OAuth2Controller.class)
@Import({JwtAuthenticationFilter.class, AuthExceptionHandler.class,
    GlobalExceptionHandler.class, OAuth2ControllerTest.TestSecurityConfig.class})
@DisplayName("OAuth2Controller")
class OAuth2ControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OAuth2Service oauth2Service;
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
              .requestMatchers("/api/auth/oauth2/**").permitAll()
              .anyRequest().authenticated())
          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
          .build();
    }
  }

  @Nested
  @DisplayName("GET /api/auth/oauth2/{provider}")
  class Initiate {

    @Test
    @DisplayName("redirects to Google authorization URL for 'google' provider")
    void initiate_googleProvider_redirectsToGoogleUrl() throws Exception {
      when(oauth2Service.getAuthorizationUrl("google"))
          .thenReturn("https://accounts.google.com/auth?client_id=xxx");

      mockMvc.perform(get("/api/auth/oauth2/google"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", "https://accounts.google.com/auth?client_id=xxx"));
    }

    @Test
    @DisplayName("redirects to GitHub authorization URL for 'github' provider")
    void initiate_githubProvider_redirectsToGithubUrl() throws Exception {
      when(oauth2Service.getAuthorizationUrl("github"))
          .thenReturn("https://github.com/login/oauth/authorize?client_id=yyy");

      mockMvc.perform(get("/api/auth/oauth2/github"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              "https://github.com/login/oauth/authorize?client_id=yyy"));
    }

    @Test
    @DisplayName("returns 400 Bad Request for unknown provider")
    void initiate_unknownProvider_returns400() throws Exception {
      when(oauth2Service.getAuthorizationUrl("facebook"))
          .thenThrow(new IllegalArgumentException("Unsupported provider: facebook"));

      mockMvc.perform(get("/api/auth/oauth2/facebook"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("GET /api/auth/oauth2/callback/{provider}")
  class Callback {

    @Test
    @DisplayName("redirects to frontend with tokens in URL fragment on success")
    void callback_validCode_redirectsToFrontendWithTokens() throws Exception {
      var authResponse = new AuthResponse("access-token", "refresh-token", "Bearer", 86400L);
      when(oauth2Service.handleCallback("google", "valid-code")).thenReturn(authResponse);

      mockMvc.perform(get("/api/auth/oauth2/callback/google")
              .param("code", "valid-code"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              org.hamcrest.Matchers.containsString("access_token=access-token")));
    }

    @Test
    @DisplayName("redirect URL contains refresh_token")
    void callback_validCode_redirectContainsRefreshToken() throws Exception {
      var authResponse = new AuthResponse("access-token", "refresh-token", "Bearer", 86400L);
      when(oauth2Service.handleCallback("google", "valid-code")).thenReturn(authResponse);

      mockMvc.perform(get("/api/auth/oauth2/callback/google")
              .param("code", "valid-code"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              org.hamcrest.Matchers.containsString("refresh_token=refresh-token")));
    }

    @Test
    @DisplayName("redirects to frontend with error on invalid code")
    void callback_invalidCode_redirectsToFrontendWithError() throws Exception {
      when(oauth2Service.handleCallback("google", "bad-code"))
          .thenThrow(new InvalidCredentialsException("OAuth2 code exchange failed"));

      mockMvc.perform(get("/api/auth/oauth2/callback/google")
              .param("code", "bad-code"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              org.hamcrest.Matchers.containsString("error=")));
    }

    @Test
    @DisplayName("redirects to frontend with error on provider conflict")
    void callback_providerConflict_redirectsToFrontendWithError() throws Exception {
      when(oauth2Service.handleCallback("google", "some-code"))
          .thenThrow(new OAuthProviderConflictException("local@example.com", "LOCAL"));

      mockMvc.perform(get("/api/auth/oauth2/callback/google")
              .param("code", "some-code"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              org.hamcrest.Matchers.containsString("error=")));
    }

    @Test
    @DisplayName("returns 400 when code query parameter is missing")
    void callback_missingCode_returns400() throws Exception {
      mockMvc.perform(get("/api/auth/oauth2/callback/google"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("redirects to frontend with error for unknown provider")
    void callback_unknownProvider_redirectsWithError() throws Exception {
      when(oauth2Service.handleCallback("twitter", "code"))
          .thenThrow(new IllegalArgumentException("Unsupported provider: twitter"));

      mockMvc.perform(get("/api/auth/oauth2/callback/twitter")
              .param("code", "code"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location",
              org.hamcrest.Matchers.containsString("error=")));
    }
  }
}
