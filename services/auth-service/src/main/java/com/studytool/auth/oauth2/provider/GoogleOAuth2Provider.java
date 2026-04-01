package com.studytool.auth.oauth2.provider;

import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.oauth2.config.OAuth2Config;
import com.studytool.auth.oauth2.dto.OAuth2TokenResponse;
import com.studytool.auth.oauth2.dto.OAuth2UserInfo;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Google OAuth2 provider implementation.
 * Uses the Google OAuth2 v2 authorization and userinfo endpoints.
 */
public class GoogleOAuth2Provider implements OAuth2Provider {

  private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2Provider.class);

  private static final String AUTHORIZATION_ENDPOINT =
      "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String SCOPE = "openid email profile";

  private final OAuth2Config.ProviderConfig config;
  private final RestTemplate restTemplate;

  public GoogleOAuth2Provider(OAuth2Config.ProviderConfig config, RestTemplate restTemplate) {
    this.config = config;
    this.restTemplate = restTemplate;
  }

  @Override
  public String getProviderName() {
    return "GOOGLE";
  }

  @Override
  public String buildAuthorizationUrl() {
    return AUTHORIZATION_ENDPOINT
        + "?client_id=" + encode(config.getClientId())
        + "&redirect_uri=" + encode(config.getRedirectUri())
        + "&response_type=code"
        + "&scope=" + encode(SCOPE)
        + "&access_type=offline";
  }

  @Override
  public OAuth2UserInfo exchangeCodeForUserInfo(String code) {
    String accessToken = exchangeCodeForAccessToken(code);
    return fetchUserInfo(accessToken);
  }

  private String exchangeCodeForAccessToken(String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("client_id", config.getClientId());
    body.add("client_secret", config.getClientSecret());
    body.add("code", code);
    body.add("grant_type", "authorization_code");
    body.add("redirect_uri", config.getRedirectUri());

    try {
      ResponseEntity<OAuth2TokenResponse> response = restTemplate.exchange(
          config.getTokenEndpoint(),
          HttpMethod.POST,
          new HttpEntity<>(body, headers),
          OAuth2TokenResponse.class);
      OAuth2TokenResponse tokenResponse = response.getBody();
      if (tokenResponse == null || tokenResponse.accessToken() == null) {
        throw new InvalidCredentialsException("Google token exchange returned empty response");
      }
      return tokenResponse.accessToken();
    } catch (HttpClientErrorException ex) {
      log.warn("Google token exchange failed with status {}: {}", ex.getStatusCode(), ex.getMessage());
      throw new InvalidCredentialsException("Google OAuth2 code exchange failed");
    }
  }

  private OAuth2UserInfo fetchUserInfo(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    ResponseEntity<Map> response = restTemplate.exchange(
        config.getUserInfoEndpoint(),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        Map.class);

    Map<?, ?> body = response.getBody();
    if (body == null) {
      throw new RuntimeException("Google userinfo endpoint returned empty response");
    }

    String id = String.valueOf(body.get("id"));
    String email = String.valueOf(body.get("email"));
    String name = String.valueOf(body.get("name"));
    return new OAuth2UserInfo(id, email, name);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
