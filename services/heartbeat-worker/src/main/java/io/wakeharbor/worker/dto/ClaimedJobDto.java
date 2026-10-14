package io.wakeharbor.worker.dto;

import java.util.UUID;

public record ClaimedJobDto(
        UUID jobId,
        UUID projectId,
        String projectRef,
        String supabaseUrl,
        String rpcEndpoint,
        String apiKey,
        String leaseToken,
        String triggerType,
        int attemptCount
) {}
