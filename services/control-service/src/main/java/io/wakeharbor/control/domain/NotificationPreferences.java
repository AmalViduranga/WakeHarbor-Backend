package io.wakeharbor.control.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "notify_on_failure", nullable = false)
    private boolean notifyOnFailure = true;

    @Column(name = "notify_on_recovery", nullable = false)
    private boolean notifyOnRecovery = true;

    @Column(name = "notify_on_worker_offline", nullable = false)
    private boolean notifyOnWorkerOffline = true;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold = 2;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 60;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationPreferences() {}

    public NotificationPreferences(UUID id, User user, boolean emailEnabled, String recipientEmail,
                                   boolean notifyOnFailure, boolean notifyOnRecovery, boolean notifyOnWorkerOffline,
                                   int failureThreshold, int cooldownMinutes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.emailEnabled = emailEnabled;
        this.recipientEmail = recipientEmail;
        this.notifyOnFailure = notifyOnFailure;
        this.notifyOnRecovery = notifyOnRecovery;
        this.notifyOnWorkerOffline = notifyOnWorkerOffline;
        this.failureThreshold = failureThreshold;
        this.cooldownMinutes = cooldownMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private User user;
        private boolean emailEnabled = false;
        private String recipientEmail;
        private boolean notifyOnFailure = true;
        private boolean notifyOnRecovery = true;
        private boolean notifyOnWorkerOffline = true;
        private int failureThreshold = 2;
        private int cooldownMinutes = 60;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder emailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; return this; }
        public Builder recipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; return this; }
        public Builder notifyOnFailure(boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; return this; }
        public Builder notifyOnRecovery(boolean notifyOnRecovery) { this.notifyOnRecovery = notifyOnRecovery; return this; }
        public Builder notifyOnWorkerOffline(boolean notifyOnWorkerOffline) { this.notifyOnWorkerOffline = notifyOnWorkerOffline; return this; }
        public Builder failureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; return this; }
        public Builder cooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public NotificationPreferences build() {
            return new NotificationPreferences(id, user, emailEnabled, recipientEmail, notifyOnFailure, notifyOnRecovery, notifyOnWorkerOffline, failureThreshold, cooldownMinutes, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public boolean isNotifyOnFailure() { return notifyOnFailure; }
    public void setNotifyOnFailure(boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; }
    public boolean isNotifyOnRecovery() { return notifyOnRecovery; }
    public void setNotifyOnRecovery(boolean notifyOnRecovery) { this.notifyOnRecovery = notifyOnRecovery; }
    public boolean isNotifyOnWorkerOffline() { return notifyOnWorkerOffline; }
    public void setNotifyOnWorkerOffline(boolean notifyOnWorkerOffline) { this.notifyOnWorkerOffline = notifyOnWorkerOffline; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
