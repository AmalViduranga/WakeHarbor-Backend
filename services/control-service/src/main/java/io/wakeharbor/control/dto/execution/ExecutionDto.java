package io.wakeharbor.control.dto.execution;

import io.wakeharbor.control.domain.enums.ExecutionStatus;
import io.wakeharbor.control.domain.enums.TriggerType;

import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
        UUID id,
        UUID projectId,
        String projectName,
        String projectRef,
        TriggerType triggerType,
        ExecutionStatus status,
        Integer httpStatusCode,
        Long latencyMs,
        String errorCategory,
        String errorDetails,
        String responsePayloadSummary,
        Instant executedAt
) {}
