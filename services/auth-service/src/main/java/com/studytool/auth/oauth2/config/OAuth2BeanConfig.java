package com.studytool.auth.oauth2.config;

import com.studytool.auth.oauth2.provider.GitHubOAuth2Provider;
import com.studytool.auth.oauth2.provider.GoogleOAuth2Provider;
import com.studytool.auth.oauth2.provider.OAuth2Provider;
import java.util.List;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Wires up OAuth2 provider beans and the shared RestTemplate used for HTTP calls
 * to Google and GitHub APIs.
 */
@Configuration
public class OAuth2BeanConfig {

  @Bean
  public RestTemplate oauth2RestTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }

  @Bean
  public GoogleOAuth2Provider googleOAuth2Provider(
      OAuth2Config oauth2Config,
      RestTemplate oauth2RestTemplate) {
    return new GoogleOAuth2Provider(oauth2Config.getGoogle(), oauth2RestTemplate);
  }

  @Bean
  public GitHubOAuth2Provider gitHubOAuth2Provider(
      OAuth2Config oauth2Config,
      RestTemplate oauth2RestTemplate) {
    return new GitHubOAuth2Provider(oauth2Config.getGithub(), oauth2RestTemplate);
  }

  @Bean
  public List<OAuth2Provider> oauth2Providers(
      GoogleOAuth2Provider googleOAuth2Provider,
      GitHubOAuth2Provider gitHubOAuth2Provider) {
    return List.of(googleOAuth2Provider, gitHubOAuth2Provider);
  }
}
