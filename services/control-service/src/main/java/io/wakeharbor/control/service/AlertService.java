package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.NotificationEvent;
import io.wakeharbor.control.domain.NotificationPreferences;
import io.wakeharbor.control.domain.SupabaseProject;
import io.wakeharbor.control.domain.enums.DeliveryStatus;
import io.wakeharbor.control.dto.alert.AlertDto;
import io.wakeharbor.control.dto.alert.NotificationPreferencesDto;
import io.wakeharbor.control.dto.alert.NotificationPreferencesUpdateRequest;
import io.wakeharbor.control.repository.NotificationEventRepository;
import io.wakeharbor.control.repository.NotificationPreferencesRepository;
import io.wakeharbor.control.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final NotificationPreferencesRepository preferencesRepository;
    private final NotificationEventRepository eventRepository;
    private final Optional<JavaMailSender> mailSender;
    private final String fromEmail;
    private final String defaultRecipient;
    private final boolean isSmtpConfigured;

    public AlertService(
            NotificationPreferencesRepository preferencesRepository,
            NotificationEventRepository eventRepository,
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${wakeharbor.notifications.from-email:alerts@wakeharbor.local}") String fromEmail,
            @Value("${wakeharbor.notifications.default-recipient:admin@wakeharbor.local}") String defaultRecipient,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.preferencesRepository = preferencesRepository;
        this.eventRepository = eventRepository;
        this.mailSender = Optional.ofNullable(mailSender);
        this.fromEmail = fromEmail;
        this.defaultRecipient = defaultRecipient;
        this.isSmtpConfigured = (smtpHost != null && !smtpHost.isBlank() && this.mailSender.isPresent());
    }

    @Transactional
    public void checkAndTriggerFailureAlert(SupabaseProject project, int consecutiveFailures, String errorDetails) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(project.getUser().getId()).orElse(null);
        int threshold = (prefs != null) ? prefs.getFailureThreshold() : 2;

        if (consecutiveFailures < threshold) {
            return;
        }

        int cooldownMins = (prefs != null) ? prefs.getCooldownMinutes() : 60;
        Instant since = Instant.now().minus(cooldownMins, ChronoUnit.MINUTES);

        // Check deduplication / cooldown
        Optional<NotificationEvent> recent = eventRepository.findRecentEvent(project.getId(), "FAILURE", since);
        if (recent.isPresent()) {
            log.debug("Skipped failure alert for project {} due to active cooldown window ({}m)", 
                    project.getProjectRef(), cooldownMins);
            return;
        }

        String recipient = (prefs != null && prefs.getRecipientEmail() != null && !prefs.getRecipientEmail().isBlank())
                ? prefs.getRecipientEmail()
                : defaultRecipient;

        String subject = "[WakeHarbor Alert] Project Heartbeat Failure: " + project.getName() + " (" + project.getProjectRef() + ")";
        String content = "WakeHarbor detected consecutive heartbeat failures for your Supabase project:\n\n" +
                "Project: " + project.getName() + " (" + project.getProjectRef() + ")\n" +
                "Consecutive Failures: " + consecutiveFailures + "\n" +
                "Error Category: " + project.getLastErrorCategory() + "\n" +
                "Details: " + errorDetails + "\n\n" +
                "Please verify your Supabase project status and ensure the database function 'public.wakeharbor_heartbeat()' is available.";

        dispatchEmail(project, "FAILURE", recipient, subject, content);
    }

    @Transactional
    public void sendRecoveryAlert(SupabaseProject project, Long latencyMs) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(project.getUser().getId()).orElse(null);
        if (prefs != null && !prefs.isNotifyOnRecovery()) {
            return;
        }

        String recipient = (prefs != null && prefs.getRecipientEmail() != null) ? prefs.getRecipientEmail() : defaultRecipient;
        String subject = "[WakeHarbor Resolved] Project Heartbeat Restored: " + project.getName();
        String content = "Good news! WakeHarbor successfully communicated with your Supabase database function:\n\n" +
                "Project: " + project.getName() + " (" + project.getProjectRef() + ")\n" +
                "Status: HEALTHY\n" +
                "Response Latency: " + latencyMs + " ms\n" +
                "Time: " + Instant.now() + "\n\n" +
                "Scheduled monitoring continues normally.";

        dispatchEmail(project, "RECOVERY", recipient, subject, content);
    }

    @Transactional
    public void sendWorkerOfflineAlert(String workerId, Instant lastHeartbeat) {
        String subject = "[WakeHarbor Warning] Heartbeat Worker Offline: " + workerId;
        String content = "The background worker process '" + workerId + "' has ceased reporting health heartbeats.\n" +
                "Last Heartbeat Seen: " + lastHeartbeat + "\n\n" +
                "WakeHarbor job leasing failover will automatically reassign any abandoned jobs.";

        dispatchEmail(null, "WORKER_OFFLINE", defaultRecipient, subject, content);
    }

    private void dispatchEmail(SupabaseProject project, String eventType, String recipient, String subject, String content) {
        DeliveryStatus status;
        String errorMsg = null;

        if (!isSmtpConfigured) {
            status = DeliveryStatus.SKIPPED_NO_SMTP;
            log.info("Notification event [{}] recorded (External SMTP not configured in environment): {}", eventType, subject);
        } else {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromEmail);
                msg.setTo(recipient);
                msg.setSubject(subject);
                msg.setText(content);
                mailSender.get().send(msg);
                status = DeliveryStatus.SENT;
                log.info("Email notification [{}] successfully delivered to {}", eventType, recipient);
            } catch (Exception e) {
                status = DeliveryStatus.FAILED;
                errorMsg = e.getMessage();
                log.error("Failed to deliver alert email to {}: {}", recipient, e.getMessage());
            }
        }

        NotificationEvent event = NotificationEvent.builder()
                .project(project)
                .eventType(eventType)
                .recipient(recipient)
                .channel("EMAIL")
                .subject(subject)
                .content(content)
                .deliveryStatus(status)
                .errorMessage(errorMsg)
                .build();

        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> getAlerts(Pageable pageable) {
        return eventRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(e -> new AlertDto(
                        e.getId(),
                        e.getProject() != null ? e.getProject().getId() : null,
                        e.getProject() != null ? e.getProject().getName() : "System",
                        e.getEventType(),
                        e.getRecipient(),
                        e.getChannel(),
                        e.getSubject(),
                        e.getContent(),
                        e.getDeliveryStatus(),
                        e.getErrorMessage(),
                        e.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(UserPrincipal principal) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(principal.id())
                .orElse(null);

        if (prefs == null) {
            return new NotificationPreferencesDto(
                    false, principal.email(), true, true, true, 2, 60, isSmtpConfigured
            );
        }

        return new NotificationPreferencesDto(
                prefs.isEmailEnabled(),
                prefs.getRecipientEmail(),
                prefs.isNotifyOnFailure(),
                prefs.isNotifyOnRecovery(),
                prefs.isNotifyOnWorkerOffline(),
                prefs.getFailureThreshold(),
                prefs.getCooldownMinutes(),
                isSmtpConfigured
        );
    }

    @Transactional
    public NotificationPreferencesDto updatePreferences(UserPrincipal principal, NotificationPreferencesUpdateRequest request) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(principal.id())
                .orElseGet(() -> NotificationPreferences.builder()
                        .user(new io.wakeharbor.control.domain.User())
                        .build());

        prefs.setEmailEnabled(request.emailEnabled());
        prefs.setRecipientEmail(request.recipientEmail());
        prefs.setNotifyOnFailure(request.notifyOnFailure());
        prefs.setNotifyOnRecovery(request.notifyOnRecovery());
        prefs.setNotifyOnWorkerOffline(request.notifyOnWorkerOffline());
        prefs.setFailureThreshold(request.failureThreshold());
        prefs.setCooldownMinutes(request.cooldownMinutes());

        NotificationPreferences saved = preferencesRepository.save(prefs);
        return new NotificationPreferencesDto(
                saved.isEmailEnabled(),
                saved.getRecipientEmail(),
                saved.isNotifyOnFailure(),
                saved.isNotifyOnRecovery(),
                saved.isNotifyOnWorkerOffline(),
                saved.getFailureThreshold(),
                saved.getCooldownMinutes(),
                isSmtpConfigured
        );
    }
}
