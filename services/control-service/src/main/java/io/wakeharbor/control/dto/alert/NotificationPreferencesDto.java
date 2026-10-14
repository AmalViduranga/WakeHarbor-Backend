package io.wakeharbor.control.dto.alert;

public record NotificationPreferencesDto(
        boolean emailEnabled,
        String recipientEmail,
        boolean notifyOnFailure,
        boolean notifyOnRecovery,
        boolean notifyOnWorkerOffline,
        int failureThreshold,
        int cooldownMinutes,
        boolean smtpConfigured
) {}
