package io.wakeharbor.control.dto.worker;

import io.wakeharbor.control.domain.enums.ErrorCategory;
import io.wakeharbor.control.domain.enums.ExecutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitJobResultRequest(
        @NotNull(message = "Job ID is required")
        UUID jobId,

        @NotBlank(message = "Lease token is required")
        String leaseToken,

        @NotBlank(message = "Worker ID is required")
        String workerId,

        @NotNull(message = "Execution status is required")
        ExecutionStatus status,

        Integer httpStatusCode,
        Long latencyMs,
        ErrorCategory errorCategory,
        String errorDetails,
        String responsePayloadSummary
) {}
