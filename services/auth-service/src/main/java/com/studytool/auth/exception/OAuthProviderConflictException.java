package com.studytool.auth.exception;

/**
 * Thrown when an OAuth2 login attempt is made for an email address that is
 * already registered with a different authentication provider.
 */
public class OAuthProviderConflictException extends RuntimeException {

  public OAuthProviderConflictException(String email, String existingProvider) {
    super("Account for " + email + " already exists with provider: " + existingProvider
        + ". Please sign in with that provider.");
  }
}
