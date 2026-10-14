package io.wakeharbor.control.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_credentials")
public class ProjectCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private SupabaseProject project;

    @Column(name = "encrypted_api_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "key_initialization_vector", nullable = false, length = 64)
    private String initializationVector;

    @Column(name = "key_auth_tag", nullable = false, length = 64)
    private String authTag;

    @Column(name = "key_preview", nullable = false, length = 32)
    private String keyPreview;

    @Column(name = "key_type", nullable = false, length = 50)
    private String keyType = "PUBLISHABLE";

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectCredentials() {}

    public ProjectCredentials(UUID id, SupabaseProject project, String encryptedApiKey, String initializationVector,
                              String authTag, String keyPreview, String keyType, int keyVersion,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.project = project;
        this.encryptedApiKey = encryptedApiKey;
        this.initializationVector = initializationVector;
        this.authTag = authTag;
        this.keyPreview = keyPreview;
        this.keyType = keyType != null ? keyType : "PUBLISHABLE";
        this.keyVersion = keyVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private SupabaseProject project;
        private String encryptedApiKey;
        private String initializationVector;
        private String authTag;
        private String keyPreview;
        private String keyType = "PUBLISHABLE";
        private int keyVersion = 1;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder project(SupabaseProject project) { this.project = project; return this; }
        public Builder encryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; return this; }
        public Builder initializationVector(String initializationVector) { this.initializationVector = initializationVector; return this; }
        public Builder authTag(String authTag) { this.authTag = authTag; return this; }
        public Builder keyPreview(String keyPreview) { this.keyPreview = keyPreview; return this; }
        public Builder keyType(String keyType) { this.keyType = keyType; return this; }
        public Builder keyVersion(int keyVersion) { this.keyVersion = keyVersion; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ProjectCredentials build() {
            return new ProjectCredentials(id, project, encryptedApiKey, initializationVector, authTag, keyPreview, keyType, keyVersion, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SupabaseProject getProject() { return project; }
    public void setProject(SupabaseProject project) { this.project = project; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }
    public String getInitializationVector() { return initializationVector; }
    public void setInitializationVector(String initializationVector) { this.initializationVector = initializationVector; }
    public String getAuthTag() { return authTag; }
    public void setAuthTag(String authTag) { this.authTag = authTag; }
    public String getKeyPreview() { return keyPreview; }
    public void setKeyPreview(String keyPreview) { this.keyPreview = keyPreview; }
    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }
    public int getKeyVersion() { return keyVersion; }
    public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
