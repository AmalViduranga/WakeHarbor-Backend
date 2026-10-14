package io.wakeharbor.control.dto.project;

import io.wakeharbor.control.domain.enums.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String projectRef,
        String supabaseUrl,
        String keyPreview,
        String heartbeatMethod,
        ProjectStatus status,
        boolean enabled,
        int intervalHours,
        Instant nextRunAt,
        Instant lastAttemptedAt,
        Instant lastSuccessfulAt,
        Long lastLatencyMs,
        String lastErrorCategory,
        String lastErrorMessage,
        int consecutiveFailures,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
