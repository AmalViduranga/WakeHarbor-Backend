package io.wakeharbor.control.dto.worker;

import jakarta.validation.constraints.NotBlank;

public record WorkerHeartbeatRequest(
        @NotBlank(message = "Worker ID is required")
        String workerId,

        String hostname,
        int currentLoad,
        long completedJobsCount,
        long failedJobsCount
) {}
