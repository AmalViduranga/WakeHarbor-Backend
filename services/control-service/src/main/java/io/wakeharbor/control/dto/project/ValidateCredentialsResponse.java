package io.wakeharbor.control.dto.project;

public record ValidateCredentialsResponse(
        boolean valid,
        String status,
        Integer httpStatusCode,
        Long latencyMs,
        String message,
        String errorCategory,
        String payloadSummary
) {}
