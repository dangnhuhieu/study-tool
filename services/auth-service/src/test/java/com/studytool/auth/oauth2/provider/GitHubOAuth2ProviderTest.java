package com.studytool.auth.oauth2.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.studytool.auth.exception.InvalidCredentialsException;
import com.studytool.auth.oauth2.config.OAuth2Config;
import com.studytool.auth.oauth2.dto.OAuth2UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@DisplayName("GitHubOAuth2Provider")
class GitHubOAuth2ProviderTest {

  private MockRestServiceServer mockServer;
  private GitHubOAuth2Provider provider;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.createServer(restTemplate);

    OAuth2Config.ProviderConfig config = new OAuth2Config.ProviderConfig(
        "test-github-client-id",
        "test-github-client-secret",
        "https://github.com/login/oauth/access_token",
        "https://api.github.com/user",
        "http://localhost:8081/api/auth/oauth2/callback/github");

    provider = new GitHubOAuth2Provider(config, restTemplate);
  }

  @Nested
  @DisplayName("getProviderName")
  class GetProviderName {

    @Test
    @DisplayName("returns GITHUB")
    void getProviderName_returnsGithub() {
      assertThat(provider.getProviderName()).isEqualTo("GITHUB");
    }
  }

  @Nested
  @DisplayName("buildAuthorizationUrl")
  class BuildAuthorizationUrl {

    @Test
    @DisplayName("contains client_id in authorization URL")
    void buildAuthorizationUrl_containsClientId() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("client_id=test-github-client-id");
    }

    @Test
    @DisplayName("contains redirect_uri in authorization URL")
    void buildAuthorizationUrl_containsRedirectUri() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("redirect_uri=");
    }

    @Test
    @DisplayName("points to GitHub OAuth authorization endpoint")
    void buildAuthorizationUrl_pointsToGithubEndpoint() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).startsWith("https://github.com/login/oauth/authorize");
    }

    @Test
    @DisplayName("contains scope with user:email")
    void buildAuthorizationUrl_containsScope() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("scope=");
    }
  }

  @Nested
  @DisplayName("exchangeCodeForUserInfo")
  class ExchangeCodeForUserInfo {

    @Test
    @DisplayName("returns OAuth2UserInfo with id, email and name on successful exchange")
    void exchangeCodeForUserInfo_validCode_returnsUserInfo() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(header("Accept", "application/json"))
          .andRespond(withSuccess(
              """
              {"access_token": "github-access-token", "token_type": "bearer", "scope": "user:email"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://api.github.com/user"))
          .andExpect(method(HttpMethod.GET))
          .andExpect(header("Authorization", "Bearer github-access-token"))
          .andRespond(withSuccess(
              """
              {"id": 42, "login": "ghuser", "email": "ghuser@example.com", "name": "GH User"}
              """, MediaType.APPLICATION_JSON));

      OAuth2UserInfo userInfo = provider.exchangeCodeForUserInfo("valid-code");

      assertThat(userInfo.id()).isEqualTo("42");
      assertThat(userInfo.email()).isEqualTo("ghuser@example.com");
      assertThat(userInfo.name()).isEqualTo("GH User");
      mockServer.verify();
    }

    @Test
    @DisplayName("uses login as name fallback when name is null")
    void exchangeCodeForUserInfo_nullName_usesLoginAsFallback() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"access_token": "github-token", "token_type": "bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://api.github.com/user"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess(
              """
              {"id": 99, "login": "mylogin", "email": "login@example.com", "name": null}
              """, MediaType.APPLICATION_JSON));

      OAuth2UserInfo userInfo = provider.exchangeCodeForUserInfo("code");

      assertThat(userInfo.name()).isEqualTo("mylogin");
      mockServer.verify();
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when token endpoint returns 4xx")
    void exchangeCodeForUserInfo_tokenEndpointReturns4xx_throwsInvalidCredentialsException() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(
              """
              {"error": "bad_verification_code"}
              """).contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("bad-code"))
          .isInstanceOf(InvalidCredentialsException.class);

      mockServer.verify();
    }

    @Test
    @DisplayName("throws RuntimeException when user info endpoint returns 5xx")
    void exchangeCodeForUserInfo_userInfoEndpointReturns500_throwsRuntimeException() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"access_token": "github-token", "token_type": "bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://api.github.com/user"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withServerError());

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("valid-code"))
          .isInstanceOf(RuntimeException.class);

      mockServer.verify();
    }

    @Test
    @DisplayName("requests JSON response from GitHub token endpoint via Accept header")
    void exchangeCodeForUserInfo_sendsAcceptJsonHeader() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(header("Accept", "application/json"))
          .andRespond(withSuccess(
              """
              {"access_token": "token", "token_type": "bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://api.github.com/user"))
          .andRespond(withSuccess(
              """
              {"id": 1, "login": "u", "email": "u@e.com", "name": "U"}
              """, MediaType.APPLICATION_JSON));

      provider.exchangeCodeForUserInfo("code");
      mockServer.verify();
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when token response has no access_token field")
    void exchangeCodeForUserInfo_tokenResponseMissingAccessToken_throwsInvalidCredentialsException() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"token_type": "bearer", "scope": "user:email"}
              """, MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("code-no-token"))
          .isInstanceOf(InvalidCredentialsException.class);

      mockServer.verify();
    }

    @Test
    @DisplayName("uses login as name when name field is empty string")
    void exchangeCodeForUserInfo_emptyStringName_usesLoginAsFallback() {
      mockServer.expect(requestTo("https://github.com/login/oauth/access_token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"access_token": "github-token", "token_type": "bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://api.github.com/user"))
          .andRespond(withSuccess(
              """
              {"id": 77, "login": "myhandle", "email": "me@gh.com", "name": "null"}
              """, MediaType.APPLICATION_JSON));

      OAuth2UserInfo userInfo = provider.exchangeCodeForUserInfo("code");

      // "null" string value triggers the null-string guard and falls back to login
      assertThat(userInfo.name()).isEqualTo("myhandle");
      mockServer.verify();
    }
  }
}
