package io.wakeharbor.control.dto.alert;

import io.wakeharbor.control.domain.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record AlertDto(
        UUID id,
        UUID projectId,
        String projectName,
        String eventType,
        String recipient,
        String channel,
        String subject,
        String content,
        DeliveryStatus deliveryStatus,
        String errorMessage,
        Instant createdAt
) {}
