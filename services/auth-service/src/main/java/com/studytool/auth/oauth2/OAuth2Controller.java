package com.studytool.auth.oauth2;

import com.studytool.auth.dto.AuthResponse;
import com.studytool.shared.dto.ApiResponse;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles OAuth2 flow initiation and provider callbacks.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client calls GET /api/auth/oauth2/{provider} → 302 redirect to provider consent page</li>
 *   <li>Provider redirects back to GET /api/auth/oauth2/callback/{provider}?code=...
 *       → 302 redirect to frontend with tokens in URL fragment</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuth2Controller {

  private static final Logger log = LoggerFactory.getLogger(OAuth2Controller.class);

  /** Frontend URL to redirect to after a successful OAuth2 login. */
  private static final String FRONTEND_REDIRECT_BASE = "http://localhost:5173/oauth2/callback";

  private final OAuth2Service oauth2Service;

  public OAuth2Controller(OAuth2Service oauth2Service) {
    this.oauth2Service = oauth2Service;
  }

  /**
   * Initiates the OAuth2 authorization flow by redirecting the user to the
   * provider's consent page.
   *
   * @param provider "google" or "github"
   * @return 302 redirect to the provider authorization URL, or 400 for unknown providers
   */
  @GetMapping("/{provider}")
  public ResponseEntity<?> initiate(@PathVariable String provider) {
    try {
      String authorizationUrl = oauth2Service.getAuthorizationUrl(provider);
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(authorizationUrl))
          .build();
    } catch (IllegalArgumentException ex) {
      log.warn("OAuth2 initiation failed for provider '{}': {}", provider, ex.getMessage());
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Unsupported OAuth2 provider: " + provider));
    }
  }

  /**
   * Handles the OAuth2 callback from the provider.
   * On success, redirects to the frontend with tokens in the URL fragment.
   * On failure, redirects to the frontend with an error fragment.
   *
   * @param provider "google" or "github"
   * @param code     the authorization code from the provider (required)
   * @return 302 redirect to frontend
   */
  @GetMapping("/callback/{provider}")
  public ResponseEntity<?> callback(
      @PathVariable String provider,
      @RequestParam String code) {
    try {
      AuthResponse tokens = oauth2Service.handleCallback(provider, code);
      String fragment = "access_token=" + tokens.accessToken()
          + "&refresh_token=" + tokens.refreshToken()
          + "&token_type=" + tokens.tokenType()
          + "&expires_in=" + tokens.expiresIn();
      URI redirectUri = URI.create(FRONTEND_REDIRECT_BASE + "#" + fragment);
      return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    } catch (Exception ex) {
      log.warn("OAuth2 callback failed for provider '{}': {}", provider, ex.getMessage());
      String errorFragment = "error=" + encodeError(ex.getMessage());
      URI redirectUri = URI.create(FRONTEND_REDIRECT_BASE + "#" + errorFragment);
      return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    }
  }

  private static String encodeError(String message) {
    if (message == null) return "oauth2_error";
    return message.replace(" ", "+");
  }
}
