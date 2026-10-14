package io.wakeharbor.control.dto.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ClaimJobsRequest(
        @NotBlank(message = "Worker ID is required")
        String workerId,

        String hostname,

        @Min(1) @Max(50)
        Integer maxBatchSize
) {}
