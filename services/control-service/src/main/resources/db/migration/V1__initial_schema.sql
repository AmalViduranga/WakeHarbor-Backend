-- WakeHarbor Database Schema - Migration V1
-- Target: PostgreSQL 14+

-- Enable pgcrypto for UUID generation if needed
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Users Table (Private single-admin bootstrap, extensible to multi-tenant)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- 2. Supabase Projects Table
CREATE TABLE supabase_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    project_ref VARCHAR(64) NOT NULL,
    supabase_url VARCHAR(512) NOT NULL,
    heartbeat_method VARCHAR(50) NOT NULL DEFAULT 'RPC_WAKEHARBOR_HEARTBEAT',
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_CONFIGURED',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    last_attempted_at TIMESTAMPTZ,
    last_successful_at TIMESTAMPTZ,
    last_latency_ms BIGINT,
    last_error_category VARCHAR(100),
    last_error_message TEXT,
    consecutive_failures INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_project_ref UNIQUE (user_id, project_ref)
);

CREATE INDEX idx_projects_status ON supabase_projects(status);
CREATE INDEX idx_projects_enabled ON supabase_projects(is_enabled);

-- 3. Project Credentials Table (Encrypted API keys with AES-256-GCM)
CREATE TABLE project_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL UNIQUE REFERENCES supabase_projects(id) ON DELETE CASCADE,
    encrypted_api_key TEXT NOT NULL,
    key_initialization_vector VARCHAR(64) NOT NULL,
    key_auth_tag VARCHAR(64) NOT NULL,
    key_preview VARCHAR(32) NOT NULL,
    key_type VARCHAR(50) NOT NULL DEFAULT 'PUBLISHABLE',
    key_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Heartbeat Schedules Table
CREATE TABLE heartbeat_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL UNIQUE REFERENCES supabase_projects(id) ON DELETE CASCADE,
    interval_hours INT NOT NULL DEFAULT 12,
    next_run_at TIMESTAMPTZ NOT NULL,
    last_run_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_schedules_next_run ON heartbeat_schedules(next_run_at) WHERE is_active = TRUE;

-- 5. Execution Jobs Table (Durable job leasing & concurrency queue)
CREATE TABLE execution_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES supabase_projects(id) ON DELETE CASCADE,
    trigger_type VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    lease_token VARCHAR(64),
    lease_owner VARCHAR(100),
    lease_expires_at TIMESTAMPTZ,
    scheduled_for TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_claimable ON execution_jobs(status, scheduled_for) 
    WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX idx_jobs_leases ON execution_jobs(status, lease_expires_at) 
    WHERE status = 'CLAIMED';
CREATE INDEX idx_jobs_project_history ON execution_jobs(project_id, created_at DESC);

-- 6. Heartbeat Executions Table (Immutable audit trail of past runs)
CREATE TABLE heartbeat_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES supabase_projects(id) ON DELETE CASCADE,
    job_id UUID REFERENCES execution_jobs(id) ON DELETE SET NULL,
    trigger_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    http_status_code INT,
    latency_ms BIGINT,
    error_category VARCHAR(100),
    error_details TEXT,
    response_payload_summary TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_project_time ON heartbeat_executions(project_id, executed_at DESC);
CREATE INDEX idx_executions_status ON heartbeat_executions(status);
CREATE INDEX idx_executions_executed_at ON heartbeat_executions(executed_at DESC);

-- 7. Notification Preferences Table
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recipient_email VARCHAR(255),
    notify_on_failure BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_recovery BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_worker_offline BOOLEAN NOT NULL DEFAULT TRUE,
    failure_threshold INT NOT NULL DEFAULT 2,
    cooldown_minutes INT NOT NULL DEFAULT 60,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Notification Events Table
CREATE TABLE notification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES supabase_projects(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL DEFAULT 'EMAIL',
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    delivery_status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_project_time ON notification_events(project_id, created_at DESC);
CREATE INDEX idx_notif_delivery_status ON notification_events(delivery_status);

-- 9. Worker Heartbeats Table (Liveness tracking of background workers)
CREATE TABLE worker_heartbeats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id VARCHAR(100) NOT NULL UNIQUE,
    hostname VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    current_load INT NOT NULL DEFAULT 0,
    completed_jobs_count BIGINT NOT NULL DEFAULT 0,
    failed_jobs_count BIGINT NOT NULL DEFAULT 0,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. Audit Events Table
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    project_id UUID REFERENCES supabase_projects(id) ON DELETE CASCADE,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_time ON audit_events(created_at DESC);
CREATE INDEX idx_audit_user ON audit_events(user_id);

-- 11. Application Settings Table
CREATE TABLE application_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert initial default application settings
INSERT INTO application_settings (key, value, description) VALUES
    ('retention_days', '30', 'Days to retain heartbeat execution records before pruning'),
    ('worker_liveness_timeout_seconds', '180', 'Seconds before a worker without heartbeat is considered offline'),
    ('default_interval_hours', '12', 'Default scheduled heartbeat interval in hours')
ON CONFLICT (key) DO NOTHING;
