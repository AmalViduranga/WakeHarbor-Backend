package io.wakeharbor.control.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String role,
        boolean active,
        Instant createdAt
) {}
