package io.wakeharbor.worker.dto;

public record ClaimJobsRequest(
        String workerId,
        String hostname,
        Integer maxBatchSize
) {}
