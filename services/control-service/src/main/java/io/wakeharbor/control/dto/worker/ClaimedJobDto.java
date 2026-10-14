package io.wakeharbor.control.dto.worker;

import io.wakeharbor.control.domain.enums.TriggerType;

import java.util.UUID;

public record ClaimedJobDto(
        UUID jobId,
        UUID projectId,
        String projectRef,
        String supabaseUrl,
        String rpcEndpoint,
        String apiKey,
        String leaseToken,
        TriggerType triggerType,
        int attemptCount
) {}
