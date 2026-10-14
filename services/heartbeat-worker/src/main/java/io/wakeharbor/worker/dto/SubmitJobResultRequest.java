package io.wakeharbor.worker.dto;

import java.util.UUID;

public record SubmitJobResultRequest(
        UUID jobId,
        String leaseToken,
        String workerId,
        String status,
        Integer httpStatusCode,
        Long latencyMs,
        String errorCategory,
        String errorDetails,
        String responsePayloadSummary
) {}
