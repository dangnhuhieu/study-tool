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
 * GitHub OAuth2 provider implementation.
 * Uses GitHub's OAuth2 authorization and user API endpoints.
 */
public class GitHubOAuth2Provider implements OAuth2Provider {

  private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2Provider.class);

  private static final String AUTHORIZATION_ENDPOINT =
      "https://github.com/login/oauth/authorize";
  private static final String SCOPE = "user:email";

  private final OAuth2Config.ProviderConfig config;
  private final RestTemplate restTemplate;

  public GitHubOAuth2Provider(OAuth2Config.ProviderConfig config, RestTemplate restTemplate) {
    this.config = config;
    this.restTemplate = restTemplate;
  }

  @Override
  public String getProviderName() {
    return "GITHUB";
  }

  @Override
  public String buildAuthorizationUrl() {
    return AUTHORIZATION_ENDPOINT
        + "?client_id=" + encode(config.getClientId())
        + "&redirect_uri=" + encode(config.getRedirectUri())
        + "&scope=" + encode(SCOPE);
  }

  @Override
  public OAuth2UserInfo exchangeCodeForUserInfo(String code) {
    String accessToken = exchangeCodeForAccessToken(code);
    return fetchUserInfo(accessToken);
  }

  private String exchangeCodeForAccessToken(String code) {
    // GitHub requires Accept: application/json to get JSON back instead of form-encoded
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("Accept", "application/json");

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("client_id", config.getClientId());
    body.add("client_secret", config.getClientSecret());
    body.add("code", code);
    body.add("redirect_uri", config.getRedirectUri());

    try {
      ResponseEntity<OAuth2TokenResponse> response = restTemplate.exchange(
          config.getTokenEndpoint(),
          HttpMethod.POST,
          new HttpEntity<>(body, headers),
          OAuth2TokenResponse.class);
      OAuth2TokenResponse tokenResponse = response.getBody();
      if (tokenResponse == null || tokenResponse.accessToken() == null) {
        throw new InvalidCredentialsException("GitHub token exchange returned empty response");
      }
      return tokenResponse.accessToken();
    } catch (HttpClientErrorException ex) {
      log.warn("GitHub token exchange failed with status {}: {}", ex.getStatusCode(), ex.getMessage());
      throw new InvalidCredentialsException("GitHub OAuth2 code exchange failed");
    }
  }

  private OAuth2UserInfo fetchUserInfo(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.set("Accept", "application/json");

    ResponseEntity<Map> response = restTemplate.exchange(
        config.getUserInfoEndpoint(),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        Map.class);

    Map<?, ?> body = response.getBody();
    if (body == null) {
      throw new RuntimeException("GitHub user endpoint returned empty response");
    }

    // GitHub id is numeric; convert to string for uniform storage
    String id = String.valueOf(body.get("id"));
    String email = String.valueOf(body.get("email"));

    // name may be null; fall back to login (username)
    Object nameObj = body.get("name");
    String name = (nameObj != null && !"null".equals(nameObj.toString()))
        ? nameObj.toString()
        : String.valueOf(body.get("login"));

    return new OAuth2UserInfo(id, email, name);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
