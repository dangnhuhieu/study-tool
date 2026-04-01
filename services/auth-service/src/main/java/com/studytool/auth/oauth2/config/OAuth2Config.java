package com.studytool.auth.oauth2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Loads OAuth2 provider configuration from application.yml under the "oauth2" prefix.
 * Uses mutable JavaBeans so Spring Boot's relaxed binder can populate nested fields.
 */
@Component
@ConfigurationProperties(prefix = "oauth2")
public class OAuth2Config {

  private final ProviderConfig google = new ProviderConfig();
  private final ProviderConfig github = new ProviderConfig();

  public ProviderConfig getGoogle() {
    return google;
  }

  public ProviderConfig getGithub() {
    return github;
  }

  /**
   * Mutable JavaBean configuration for a single OAuth2 provider.
   * Immutable snapshot available via {@link #snapshot()}.
   */
  public static class ProviderConfig {

    private String clientId = "";
    private String clientSecret = "";
    private String tokenEndpoint = "";
    private String userInfoEndpoint = "";
    private String redirectUri = "";

    /** No-arg constructor required by Spring Boot binder. */
    public ProviderConfig() {}

    /** Convenience constructor used in tests. */
    public ProviderConfig(
        String clientId,
        String clientSecret,
        String tokenEndpoint,
        String userInfoEndpoint,
        String redirectUri) {
      this.clientId = clientId;
      this.clientSecret = clientSecret;
      this.tokenEndpoint = tokenEndpoint;
      this.userInfoEndpoint = userInfoEndpoint;
      this.redirectUri = redirectUri;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getTokenEndpoint() { return tokenEndpoint; }
    public void setTokenEndpoint(String tokenEndpoint) { this.tokenEndpoint = tokenEndpoint; }

    public String getUserInfoEndpoint() { return userInfoEndpoint; }
    public void setUserInfoEndpoint(String userInfoEndpoint) {
      this.userInfoEndpoint = userInfoEndpoint;
    }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
  }
}
