package io.wakeharbor.worker.dto;

public record WorkerHeartbeatRequest(
        String workerId,
        String hostname,
        int currentLoad,
        long completedJobsCount,
        long failedJobsCount
) {}
