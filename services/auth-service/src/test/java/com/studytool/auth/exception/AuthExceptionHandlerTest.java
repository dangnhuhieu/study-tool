package com.studytool.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.studytool.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("AuthExceptionHandler")
class AuthExceptionHandlerTest {

  private AuthExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new AuthExceptionHandler();
  }

  @Test
  @DisplayName("returns 409 Conflict for UserAlreadyExistsException")
  void handleUserAlreadyExists_returns409WithMessage() {
    var ex = new UserAlreadyExistsException("dup@example.com");

    ResponseEntity<ApiResponse<Void>> response = handler.handleUserAlreadyExists(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).contains("dup@example.com");
  }

  @Test
  @DisplayName("returns 401 Unauthorized for InvalidCredentialsException")
  void handleInvalidCredentials_returns401() {
    var ex = new InvalidCredentialsException("bad credentials");

    ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidCredentials(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  @Test
  @DisplayName("returns 409 Conflict for OAuthProviderConflictException with provider in message")
  void handleOAuthProviderConflict_returns409WithProviderInMessage() {
    var ex = new OAuthProviderConflictException("user@example.com", "LOCAL");

    ResponseEntity<ApiResponse<Void>> response = handler.handleOAuthProviderConflict(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).contains("LOCAL");
  }

  @Test
  @DisplayName("returns 400 Bad Request for IllegalArgumentException with message preserved")
  void handleIllegalArgument_returns400WithMessage() {
    var ex = new IllegalArgumentException("Unsupported provider: twitter");

    ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).contains("twitter");
  }
}
