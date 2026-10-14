# WakeHarbor — Product Requirements Document (PRD)

**Product Name:** WakeHarbor  
**Tagline:** Keep your development projects active, intelligently.  
**Release Target:** v1.0 Private, Single-Admin MVP  
**Architectural Baseline:** Multi-tenant ready, single-tenant locked  

---

## 1. Executive Summary

Supabase Free Plan projects are automatically paused when they receive insufficient user database activity over a rolling 7-day period. For independent developers, side-project builders, and staging environments, project pausing introduces cold delays, broken continuous integration pipelines, and failed customer demos.

WakeHarbor solves this by providing an autonomous, reliable, and secure platform that schedules lightweight, safe database heartbeats against Supabase Free Plan instances, records verified execution outcomes, detects failures, and alerts administrators before downtime occurs.

**Critical Policy Notice:** WakeHarbor provides evidence of active database queries via authorized RPC execution; it *never* claims or guarantees that Supabase will not pause a project, as Supabase retains exclusive authority over its inactivity rules.

---

## 2. Target Persona & Release Model

### Phase 1: Private Single-Admin MVP
- **Primary User:** System Administrator / Engineering Lead.
- **Access Control:** Single bootstrap administrator created via environment variables or secure initialization.
- **Public Surface:** Public registration, team billing, Stripe subscriptions, and multi-user invites are explicitly disabled to prevent abuse and unauthorized outbound scanning.
- **Architectural Readiness:** All database models enforce explicit `user_id` ownership relations so multi-tenant isolation, user quotas, and organization roles can be enabled in subsequent releases without schema redesign.

---

## 3. Core Functional Requirements

### 3.1 Project Connection & Onboarding
- **FR-1.1 Hostname Restriction:** Only valid Supabase subdomains matching `[a-z0-9_-]+(\.supabase\.(co|in))` or strictly whitelisted hostnames are accepted. Arbitrary IP addresses, localhost, RFC 1918 private subnets, and non-Supabase hosts must be rejected immediately.
- **FR-1.2 Least Privilege Credentials:** Only Supabase Publishable API keys (`sb_publishable_...` or legacy anon keys) are permitted. Database administrative passwords, `service_role` keys, and Supabase Management tokens are strictly rejected.
- **FR-1.3 Safe Heartbeat SQL Function:** Guided setup requires the user to install a lightweight, read-only SQL RPC function:
  ```sql
  CREATE OR REPLACE FUNCTION public.wakeharbor_heartbeat()
  RETURNS jsonb
  LANGUAGE sql
  STABLE
  SECURITY INVOKER
  SET search_path = ''
  AS $$
    SELECT jsonb_build_object(
      'ok', true,
      'server_time', now()
    );
  $$;
  ```
- **FR-1.4 Real-time Verification:** The onboarding wizard must invoke a live RPC verification test before persisting the project schedule.

### 3.2 Autonomous Scheduling & Execution
- **FR-2.1 Supported Cadences:** Configurable intervals (Every 6 hours, Every 12 hours [default], Every 24 hours).
- **FR-2.2 Durable Job Leases:** Background execution relies on persistent leases in PostgreSQL (`execution_jobs` table) with row-level locks (`SELECT ... FOR UPDATE SKIP LOCKED`).
- **FR-2.3 Worker Crash Recovery:** Unacknowledged or timed-out leases (older than 5 minutes) are automatically reclaimed and rescheduled with exponential backoff.
- **FR-2.4 Independent Execution:** Heartbeat requests run autonomously in the `heartbeat-worker` service, completely independent of whether any browser dashboard is open.

### 3.3 Diagnostic Classification
- **FR-3.1 Response Categorization:** Every execution outcome is categorized into structured failure codes:
  - `SUCCESS`: Verified `{ "ok": true }` payload.
  - `AUTHENTICATION_FAILED`: HTTP 401 / 403 API key error.
  - `FUNCTION_NOT_FOUND`: HTTP 404 RPC endpoint missing.
  - `PROJECT_PAUSED`: Supabase upstream 503 / 522 / paused indicators.
  - `RATE_LIMITED`: HTTP 429 response.
  - `TIMEOUT`: Network connection/read timeout (> 15 seconds).
  - `SSRF_BLOCKED`: Target hostname attempted resolution to private IP.
- **FR-3.2 Circuit Breaking:** Permanent authentication errors pause automatic scheduling after 5 consecutive failures to prevent wasteful outbound requests.

### 3.4 Alerts & Health Monitoring
- **FR-4.1 Failure Notifications:** Generates incident alerts when consecutive failures exceed configured thresholds.
- **FR-4.2 Recovery Signals:** Emits resolution alerts when a previously failing project recovers.
- **FR-4.3 Worker Telemetry:** Emits alerts if the background worker process fails to send a liveness ping within 3 minutes.
- **FR-4.4 Alert Cooldown:** Enforces a 60-minute deduplication window per project to prevent notification flooding.

---

## 4. Non-Functional Requirements

### 4.1 Security
- All sensitive credentials encrypted using AES-256-GCM with PBKDF2 key derivation and randomized 96-bit initialization vectors.
- Strict SSRF validation: custom DNS pre-resolution prohibiting loopback (`127.0.0.0/8`, `::1`), private networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), and link-local ranges (`169.254.0.0/16`).
- Automatic HTTP redirects disabled in external HTTP clients.
- Authentication cookies issued with `HttpOnly`, `SameSite=Lax`, and `Secure` (in production).
- Zero-credential logging: API keys masked to first 6 and last 4 characters in all API responses and stripped from logs.

### 4.2 Reliability & Fault Tolerance
- Worker and control services deployable independently.
- Database transactions enforce atomic job state transitions.
- Idempotent result submissions keyed by `job_id` and `lease_token`.

### 4.3 Observability
- Spring Boot Actuator health endpoints (`/actuator/health`, `/actuator/info`).
- Execution duration, HTTP status codes, and error categories tracked per run.
- Log retention cleanup routines preventing unbounded database growth.
