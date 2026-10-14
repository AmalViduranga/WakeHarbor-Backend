package io.wakeharbor.control.domain;

import io.wakeharbor.control.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_events")
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private SupabaseProject project;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 50)
    private String channel = "EMAIL";

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 50)
    private DeliveryStatus deliveryStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public NotificationEvent() {}

    public NotificationEvent(UUID id, SupabaseProject project, String eventType, String recipient,
                             String channel, String subject, String content, DeliveryStatus deliveryStatus,
                             String errorMessage, Instant createdAt) {
        this.id = id;
        this.project = project;
        this.eventType = eventType;
        this.recipient = recipient;
        this.channel = channel != null ? channel : "EMAIL";
        this.subject = subject;
        this.content = content;
        this.deliveryStatus = deliveryStatus;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private SupabaseProject project;
        private String eventType;
        private String recipient;
        private String channel = "EMAIL";
        private String subject;
        private String content;
        private DeliveryStatus deliveryStatus;
        private String errorMessage;
        private Instant createdAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder project(SupabaseProject project) { this.project = project; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder recipient(String recipient) { this.recipient = recipient; return this; }
        public Builder channel(String channel) { this.channel = channel; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder deliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public NotificationEvent build() {
            return new NotificationEvent(id, project, eventType, recipient, channel, subject, content, deliveryStatus, errorMessage, createdAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SupabaseProject getProject() { return project; }
    public void setProject(SupabaseProject project) { this.project = project; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
