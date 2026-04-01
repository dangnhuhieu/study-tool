package com.studytool.auth.oauth2.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

@DisplayName("GoogleOAuth2Provider")
class GoogleOAuth2ProviderTest {

  private MockRestServiceServer mockServer;
  private GoogleOAuth2Provider provider;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.createServer(restTemplate);

    OAuth2Config.ProviderConfig config = new OAuth2Config.ProviderConfig(
        "test-google-client-id",
        "test-google-client-secret",
        "https://oauth2.googleapis.com/token",
        "https://www.googleapis.com/oauth2/v2/userinfo",
        "http://localhost:8081/api/auth/oauth2/callback/google");

    provider = new GoogleOAuth2Provider(config, restTemplate);
  }

  @Nested
  @DisplayName("getProviderName")
  class GetProviderName {

    @Test
    @DisplayName("returns GOOGLE")
    void getProviderName_returnsGoogle() {
      assertThat(provider.getProviderName()).isEqualTo("GOOGLE");
    }
  }

  @Nested
  @DisplayName("buildAuthorizationUrl")
  class BuildAuthorizationUrl {

    @Test
    @DisplayName("contains client_id in authorization URL")
    void buildAuthorizationUrl_containsClientId() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("client_id=test-google-client-id");
    }

    @Test
    @DisplayName("contains redirect_uri in authorization URL")
    void buildAuthorizationUrl_containsRedirectUri() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("redirect_uri=");
    }

    @Test
    @DisplayName("contains response_type=code in authorization URL")
    void buildAuthorizationUrl_containsResponseTypeCode() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("response_type=code");
    }

    @Test
    @DisplayName("contains scope with email and profile")
    void buildAuthorizationUrl_containsScope() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).contains("scope=");
    }

    @Test
    @DisplayName("points to Google accounts authorization endpoint")
    void buildAuthorizationUrl_pointsToGoogleEndpoint() {
      String url = provider.buildAuthorizationUrl();
      assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
    }
  }

  @Nested
  @DisplayName("exchangeCodeForUserInfo")
  class ExchangeCodeForUserInfo {

    @Test
    @DisplayName("returns OAuth2UserInfo with email and name on successful exchange")
    void exchangeCodeForUserInfo_validCode_returnsUserInfo() {
      mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"access_token": "google-access-token", "token_type": "Bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess(
              """
              {"id": "google-uid-123", "email": "user@gmail.com", "name": "Google User"}
              """, MediaType.APPLICATION_JSON));

      OAuth2UserInfo userInfo = provider.exchangeCodeForUserInfo("valid-code");

      assertThat(userInfo.id()).isEqualTo("google-uid-123");
      assertThat(userInfo.email()).isEqualTo("user@gmail.com");
      assertThat(userInfo.name()).isEqualTo("Google User");
      mockServer.verify();
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when token endpoint returns 4xx")
    void exchangeCodeForUserInfo_tokenEndpointReturns400_throwsInvalidCredentialsException() {
      mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(
              """
              {"error": "invalid_grant"}
              """).contentType(MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("bad-code"))
          .isInstanceOf(InvalidCredentialsException.class);

      mockServer.verify();
    }

    @Test
    @DisplayName("throws RuntimeException when user info endpoint returns 5xx")
    void exchangeCodeForUserInfo_userInfoEndpointReturns500_throwsRuntimeException() {
      mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"access_token": "google-access-token", "token_type": "Bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withServerError());

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("valid-code"))
          .isInstanceOf(RuntimeException.class);

      mockServer.verify();
    }

    @Test
    @DisplayName("sends correct client_id, client_secret, code in token request body")
    void exchangeCodeForUserInfo_sendsCorrectTokenRequestParams() {
      mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(content().string(
              org.hamcrest.Matchers.allOf(
                  org.hamcrest.Matchers.containsString("client_id=test-google-client-id"),
                  org.hamcrest.Matchers.containsString("client_secret=test-google-client-secret"),
                  org.hamcrest.Matchers.containsString("code=test-auth-code"),
                  org.hamcrest.Matchers.containsString("grant_type=authorization_code"))))
          .andRespond(withSuccess(
              """
              {"access_token": "token", "token_type": "Bearer"}
              """, MediaType.APPLICATION_JSON));

      mockServer.expect(requestTo("https://www.googleapis.com/oauth2/v2/userinfo"))
          .andRespond(withSuccess(
              """
              {"id": "uid", "email": "e@g.com", "name": "E"}
              """, MediaType.APPLICATION_JSON));

      provider.exchangeCodeForUserInfo("test-auth-code");
      mockServer.verify();
    }

    @Test
    @DisplayName("throws InvalidCredentialsException when token response has no access_token field")
    void exchangeCodeForUserInfo_tokenResponseMissingAccessToken_throwsInvalidCredentialsException() {
      mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess(
              """
              {"token_type": "Bearer"}
              """, MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> provider.exchangeCodeForUserInfo("code-without-token"))
          .isInstanceOf(InvalidCredentialsException.class);

      mockServer.verify();
    }
  }
}
