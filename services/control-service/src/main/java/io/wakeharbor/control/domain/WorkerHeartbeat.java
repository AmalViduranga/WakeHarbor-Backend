package io.wakeharbor.control.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_heartbeats")
public class WorkerHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "worker_id", nullable = false, unique = true, length = 100)
    private String workerId;

    @Column
    private String hostname;

    @Column(nullable = false, length = 50)
    private String status = "ONLINE";

    @Column(name = "current_load", nullable = false)
    private int currentLoad = 0;

    @Column(name = "completed_jobs_count", nullable = false)
    private long completedJobsCount = 0;

    @Column(name = "failed_jobs_count", nullable = false)
    private long failedJobsCount = 0;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkerHeartbeat() {}

    public WorkerHeartbeat(UUID id, String workerId, String hostname, String status, int currentLoad,
                           long completedJobsCount, long failedJobsCount, Instant lastHeartbeatAt,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.workerId = workerId;
        this.hostname = hostname;
        this.status = status != null ? status : "ONLINE";
        this.currentLoad = currentLoad;
        this.completedJobsCount = completedJobsCount;
        this.failedJobsCount = failedJobsCount;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String workerId;
        private String hostname;
        private String status = "ONLINE";
        private int currentLoad = 0;
        private long completedJobsCount = 0;
        private long failedJobsCount = 0;
        private Instant lastHeartbeatAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder workerId(String workerId) { this.workerId = workerId; return this; }
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder currentLoad(int currentLoad) { this.currentLoad = currentLoad; return this; }
        public Builder completedJobsCount(long completedJobsCount) { this.completedJobsCount = completedJobsCount; return this; }
        public Builder failedJobsCount(long failedJobsCount) { this.failedJobsCount = failedJobsCount; return this; }
        public Builder lastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public WorkerHeartbeat build() {
            return new WorkerHeartbeat(id, workerId, hostname, status, currentLoad, completedJobsCount, failedJobsCount, lastHeartbeatAt, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(int currentLoad) { this.currentLoad = currentLoad; }
    public long getCompletedJobsCount() { return completedJobsCount; }
    public void setCompletedJobsCount(long completedJobsCount) { this.completedJobsCount = completedJobsCount; }
    public long getFailedJobsCount() { return failedJobsCount; }
    public void setFailedJobsCount(long failedJobsCount) { this.failedJobsCount = failedJobsCount; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
