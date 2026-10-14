package io.wakeharbor.control.domain;

import io.wakeharbor.control.domain.enums.ExecutionStatus;
import io.wakeharbor.control.domain.enums.TriggerType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "heartbeat_executions")
public class HeartbeatExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private SupabaseProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ExecutionJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExecutionStatus status;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_category", length = 100)
    private String errorCategory;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "response_payload_summary", columnDefinition = "TEXT")
    private String responsePayloadSummary;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public HeartbeatExecution() {}

    public HeartbeatExecution(UUID id, SupabaseProject project, ExecutionJob job, TriggerType triggerType,
                              ExecutionStatus status, Integer httpStatusCode, Long latencyMs,
                              String errorCategory, String errorDetails, String responsePayloadSummary,
                              Instant executedAt, Instant createdAt) {
        this.id = id;
        this.project = project;
        this.job = job;
        this.triggerType = triggerType;
        this.status = status;
        this.httpStatusCode = httpStatusCode;
        this.latencyMs = latencyMs;
        this.errorCategory = errorCategory;
        this.errorDetails = errorDetails;
        this.responsePayloadSummary = responsePayloadSummary;
        this.executedAt = executedAt;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private SupabaseProject project;
        private ExecutionJob job;
        private TriggerType triggerType;
        private ExecutionStatus status;
        private Integer httpStatusCode;
        private Long latencyMs;
        private String errorCategory;
        private String errorDetails;
        private String responsePayloadSummary;
        private Instant executedAt;
        private Instant createdAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder project(SupabaseProject project) { this.project = project; return this; }
        public Builder job(ExecutionJob job) { this.job = job; return this; }
        public Builder triggerType(TriggerType triggerType) { this.triggerType = triggerType; return this; }
        public Builder status(ExecutionStatus status) { this.status = status; return this; }
        public Builder httpStatusCode(Integer httpStatusCode) { this.httpStatusCode = httpStatusCode; return this; }
        public Builder latencyMs(Long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder errorCategory(String errorCategory) { this.errorCategory = errorCategory; return this; }
        public Builder errorDetails(String errorDetails) { this.errorDetails = errorDetails; return this; }
        public Builder responsePayloadSummary(String responsePayloadSummary) { this.responsePayloadSummary = responsePayloadSummary; return this; }
        public Builder executedAt(Instant executedAt) { this.executedAt = executedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public HeartbeatExecution build() {
            return new HeartbeatExecution(id, project, job, triggerType, status, httpStatusCode, latencyMs, errorCategory, errorDetails, responsePayloadSummary, executedAt, createdAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SupabaseProject getProject() { return project; }
    public void setProject(SupabaseProject project) { this.project = project; }
    public ExecutionJob getJob() { return job; }
    public void setJob(ExecutionJob job) { this.job = job; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public Integer getHttpStatusCode() { return httpStatusCode; }
    public void setHttpStatusCode(Integer httpStatusCode) { this.httpStatusCode = httpStatusCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
    public String getResponsePayloadSummary() { return responsePayloadSummary; }
    public void setResponsePayloadSummary(String responsePayloadSummary) { this.responsePayloadSummary = responsePayloadSummary; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
