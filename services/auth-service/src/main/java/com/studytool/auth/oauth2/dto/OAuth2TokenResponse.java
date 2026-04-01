package com.studytool.auth.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw token response returned by an OAuth2 provider's token endpoint.
 */
public record OAuth2TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("scope") String scope) {}
