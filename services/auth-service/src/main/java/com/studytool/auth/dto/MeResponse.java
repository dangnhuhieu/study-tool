package com.studytool.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String name,
    String role,
    String provider,
    Instant createdAt
) {}
