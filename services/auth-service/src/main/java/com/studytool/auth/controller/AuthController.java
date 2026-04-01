package com.studytool.auth.controller;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.auth.dto.LoginRequest;
import com.studytool.auth.dto.MeResponse;
import com.studytool.auth.dto.RefreshTokenRequest;
import com.studytool.auth.dto.RegisterRequest;
import com.studytool.auth.service.UserService;
import com.studytool.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResponse response = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    AuthResponse response = userService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    AuthResponse response = userService.refreshToken(request.refreshToken());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<MeResponse>> me(
      @AuthenticationPrincipal String userId) {
    MeResponse response = userService.getMe(userId);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
