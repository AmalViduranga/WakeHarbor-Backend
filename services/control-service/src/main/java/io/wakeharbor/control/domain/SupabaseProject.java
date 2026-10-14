package io.wakeharbor.control.domain;

import io.wakeharbor.control.domain.enums.ProjectStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supabase_projects")
public class SupabaseProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name = "project_ref", nullable = false, length = 64)
    private String projectRef;

    @Column(name = "supabase_url", nullable = false, length = 512)
    private String supabaseUrl;

    @Column(name = "heartbeat_method", nullable = false, length = 50)
    private String heartbeatMethod = "RPC_WAKEHARBOR_HEARTBEAT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProjectStatus status = ProjectStatus.NOT_CONFIGURED;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "last_successful_at")
    private Instant lastSuccessfulAt;

    @Column(name = "last_latency_ms")
    private Long lastLatencyMs;

    @Column(name = "last_error_category", length = 100)
    private String lastErrorCategory;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private ProjectCredentials credentials;

    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private HeartbeatSchedule schedule;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupabaseProject() {}

    public SupabaseProject(UUID id, User user, String name, String projectRef, String supabaseUrl, String heartbeatMethod,
                           ProjectStatus status, boolean enabled, String notes, Instant lastAttemptedAt,
                           Instant lastSuccessfulAt, Long lastLatencyMs, String lastErrorCategory, String lastErrorMessage,
                           int consecutiveFailures, ProjectCredentials credentials, HeartbeatSchedule schedule,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.projectRef = projectRef;
        this.supabaseUrl = supabaseUrl;
        this.heartbeatMethod = heartbeatMethod != null ? heartbeatMethod : "RPC_WAKEHARBOR_HEARTBEAT";
        this.status = status != null ? status : ProjectStatus.NOT_CONFIGURED;
        this.enabled = enabled;
        this.notes = notes;
        this.lastAttemptedAt = lastAttemptedAt;
        this.lastSuccessfulAt = lastSuccessfulAt;
        this.lastLatencyMs = lastLatencyMs;
        this.lastErrorCategory = lastErrorCategory;
        this.lastErrorMessage = lastErrorMessage;
        this.consecutiveFailures = consecutiveFailures;
        this.credentials = credentials;
        this.schedule = schedule;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private User user;
        private String name;
        private String projectRef;
        private String supabaseUrl;
        private String heartbeatMethod = "RPC_WAKEHARBOR_HEARTBEAT";
        private ProjectStatus status = ProjectStatus.NOT_CONFIGURED;
        private boolean enabled = true;
        private String notes;
        private Instant lastAttemptedAt;
        private Instant lastSuccessfulAt;
        private Long lastLatencyMs;
        private String lastErrorCategory;
        private String lastErrorMessage;
        private int consecutiveFailures = 0;
        private ProjectCredentials credentials;
        private HeartbeatSchedule schedule;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder projectRef(String projectRef) { this.projectRef = projectRef; return this; }
        public Builder supabaseUrl(String supabaseUrl) { this.supabaseUrl = supabaseUrl; return this; }
        public Builder heartbeatMethod(String heartbeatMethod) { this.heartbeatMethod = heartbeatMethod; return this; }
        public Builder status(ProjectStatus status) { this.status = status; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder lastAttemptedAt(Instant lastAttemptedAt) { this.lastAttemptedAt = lastAttemptedAt; return this; }
        public Builder lastSuccessfulAt(Instant lastSuccessfulAt) { this.lastSuccessfulAt = lastSuccessfulAt; return this; }
        public Builder lastLatencyMs(Long lastLatencyMs) { this.lastLatencyMs = lastLatencyMs; return this; }
        public Builder lastErrorCategory(String lastErrorCategory) { this.lastErrorCategory = lastErrorCategory; return this; }
        public Builder lastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; return this; }
        public Builder consecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; return this; }
        public Builder credentials(ProjectCredentials credentials) { this.credentials = credentials; return this; }
        public Builder schedule(HeartbeatSchedule schedule) { this.schedule = schedule; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public SupabaseProject build() {
            return new SupabaseProject(id, user, name, projectRef, supabaseUrl, heartbeatMethod, status, enabled, notes,
                    lastAttemptedAt, lastSuccessfulAt, lastLatencyMs, lastErrorCategory, lastErrorMessage,
                    consecutiveFailures, credentials, schedule, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProjectRef() { return projectRef; }
    public void setProjectRef(String projectRef) { this.projectRef = projectRef; }
    public String getSupabaseUrl() { return supabaseUrl; }
    public void setSupabaseUrl(String supabaseUrl) { this.supabaseUrl = supabaseUrl; }
    public String getHeartbeatMethod() { return heartbeatMethod; }
    public void setHeartbeatMethod(String heartbeatMethod) { this.heartbeatMethod = heartbeatMethod; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getLastAttemptedAt() { return lastAttemptedAt; }
    public void setLastAttemptedAt(Instant lastAttemptedAt) { this.lastAttemptedAt = lastAttemptedAt; }
    public Instant getLastSuccessfulAt() { return lastSuccessfulAt; }
    public void setLastSuccessfulAt(Instant lastSuccessfulAt) { this.lastSuccessfulAt = lastSuccessfulAt; }
    public Long getLastLatencyMs() { return lastLatencyMs; }
    public void setLastLatencyMs(Long lastLatencyMs) { this.lastLatencyMs = lastLatencyMs; }
    public String getLastErrorCategory() { return lastErrorCategory; }
    public void setLastErrorCategory(String lastErrorCategory) { this.lastErrorCategory = lastErrorCategory; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public ProjectCredentials getCredentials() { return credentials; }
    public void setCredentials(ProjectCredentials credentials) { this.credentials = credentials; }
    public HeartbeatSchedule getSchedule() { return schedule; }
    public void setSchedule(HeartbeatSchedule schedule) { this.schedule = schedule; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
