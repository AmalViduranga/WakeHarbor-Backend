package io.wakeharbor.control.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "heartbeat_schedules")
public class HeartbeatSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private SupabaseProject project;

    @Column(name = "interval_hours", nullable = false)
    private int intervalHours = 12;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HeartbeatSchedule() {}

    public HeartbeatSchedule(UUID id, SupabaseProject project, int intervalHours, Instant nextRunAt, Instant lastRunAt, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.project = project;
        this.intervalHours = intervalHours;
        this.nextRunAt = nextRunAt;
        this.lastRunAt = lastRunAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private SupabaseProject project;
        private int intervalHours = 12;
        private Instant nextRunAt;
        private Instant lastRunAt;
        private boolean active = true;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder project(SupabaseProject project) { this.project = project; return this; }
        public Builder intervalHours(int intervalHours) { this.intervalHours = intervalHours; return this; }
        public Builder nextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; return this; }
        public Builder lastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public HeartbeatSchedule build() {
            return new HeartbeatSchedule(id, project, intervalHours, nextRunAt, lastRunAt, active, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SupabaseProject getProject() { return project; }
    public void setProject(SupabaseProject project) { this.project = project; }
    public int getIntervalHours() { return intervalHours; }
    public void setIntervalHours(int intervalHours) { this.intervalHours = intervalHours; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
