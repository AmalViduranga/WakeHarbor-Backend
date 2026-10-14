package io.wakeharbor.control.dto.alert;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationPreferencesUpdateRequest(
        boolean emailEnabled,

        @Email(message = "Invalid email format")
        String recipientEmail,

        boolean notifyOnFailure,
        boolean notifyOnRecovery,
        boolean notifyOnWorkerOffline,

        @Min(1) @Max(10)
        int failureThreshold,

        @Min(5) @Max(1440)
        int cooldownMinutes
) {}
