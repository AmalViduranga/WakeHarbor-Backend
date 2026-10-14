package io.wakeharbor.control.domain;

import io.wakeharbor.control.domain.enums.JobStatus;
import io.wakeharbor.control.domain.enums.TriggerType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_jobs")
public class ExecutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private SupabaseProject project;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType = TriggerType.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "lease_token", length = 64)
    private String leaseToken;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ExecutionJob() {}

    public ExecutionJob(UUID id, SupabaseProject project, TriggerType triggerType, JobStatus status,
                        int attemptCount, int maxAttempts, String leaseToken, String leaseOwner,
                        Instant leaseExpiresAt, Instant scheduledFor, Instant claimedAt,
                        Instant completedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.project = project;
        this.triggerType = triggerType != null ? triggerType : TriggerType.SCHEDULED;
        this.status = status != null ? status : JobStatus.PENDING;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.leaseToken = leaseToken;
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.scheduledFor = scheduledFor;
        this.claimedAt = claimedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private SupabaseProject project;
        private TriggerType triggerType = TriggerType.SCHEDULED;
        private JobStatus status = JobStatus.PENDING;
        private int attemptCount = 0;
        private int maxAttempts = 3;
        private String leaseToken;
        private String leaseOwner;
        private Instant leaseExpiresAt;
        private Instant scheduledFor;
        private Instant claimedAt;
        private Instant completedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder project(SupabaseProject project) { this.project = project; return this; }
        public Builder triggerType(TriggerType triggerType) { this.triggerType = triggerType; return this; }
        public Builder status(JobStatus status) { this.status = status; return this; }
        public Builder attemptCount(int attemptCount) { this.attemptCount = attemptCount; return this; }
        public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder leaseToken(String leaseToken) { this.leaseToken = leaseToken; return this; }
        public Builder leaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; return this; }
        public Builder leaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; return this; }
        public Builder scheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; return this; }
        public Builder claimedAt(Instant claimedAt) { this.claimedAt = claimedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ExecutionJob build() {
            return new ExecutionJob(id, project, triggerType, status, attemptCount, maxAttempts, leaseToken, leaseOwner, leaseExpiresAt, scheduledFor, claimedAt, completedAt, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SupabaseProject getProject() { return project; }
    public void setProject(SupabaseProject project) { this.project = project; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
