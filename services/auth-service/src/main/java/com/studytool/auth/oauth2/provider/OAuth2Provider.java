package com.studytool.auth.oauth2.provider;

import com.studytool.auth.oauth2.dto.OAuth2UserInfo;

/**
 * Strategy interface for OAuth2 provider-specific logic.
 * Each implementation handles one provider (Google, GitHub, etc.).
 */
public interface OAuth2Provider {

  /**
   * Returns the canonical uppercase provider name stored in the User entity,
   * e.g. "GOOGLE" or "GITHUB".
   */
  String getProviderName();

  /**
   * Builds the authorization URL to redirect the user to for consent.
   */
  String buildAuthorizationUrl();

  /**
   * Exchanges a one-time authorization code for an access token, then fetches
   * and returns the user's profile information from the provider.
   *
   * @param code the authorization code from the OAuth2 callback
   * @return normalized user info from the provider
   * @throws com.studytool.auth.exception.InvalidCredentialsException if the code exchange fails
   * @throws RuntimeException if the user-info fetch fails with a server error
   */
  OAuth2UserInfo exchangeCodeForUserInfo(String code);
}
