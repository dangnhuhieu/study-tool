package com.studytool.auth.oauth2.dto;

/**
 * Normalized user information extracted from an OAuth2 provider response.
 *
 * @param id    the provider-scoped unique identifier (e.g. Google sub, GitHub id)
 * @param email the user's verified email address from the provider
 * @param name  the user's display name from the provider
 */
public record OAuth2UserInfo(String id, String email, String name) {}
