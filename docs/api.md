# WakeHarbor — REST API Reference

**Base URL:** `/api/v1`  
**Internal Base URL:** `/api/internal/v1`  
**Authentication:** Cookie-based (`wakeharbor_session`) for Public Admin API; Header-based (`X-Worker-Token`) for Internal Worker API.  

---

## 1. Authentication Endpoints

### 1.1 POST `/api/v1/auth/login`
Authenticates the bootstrap administrator and sets a secure HttpOnly session cookie.

**Request Body:**
```json
{
  "email": "admin@wakeharbor.local",
  "password": "your-secure-password"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOi...",
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "admin@wakeharbor.local",
  "role": "ADMIN"
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid email or password.
- `429 Too Many Requests`: Login rate limit exceeded (5 attempts per minute).

---

### 1.2 POST `/api/v1/auth/logout`
Invalidates the current session and clears the authentication cookie.

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

---

### 1.3 GET `/api/v1/auth/me`
Retrieves information about the currently authenticated administrator.

**Response (200 OK):**
```json
{
  "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "admin@wakeharbor.local",
  "role": "ADMIN",
  "createdAt": "2026-10-14T18:00:00Z"
}
```

---

## 2. Project Management Endpoints

### 2.1 GET `/api/v1/projects`
Lists all configured Supabase projects.

**Response (200 OK):**
```json
[
  {
    "id": "c1f7a0b3-e5d2-4a81-9b88-51f67f62e6e1",
    "name": "E-Commerce Staging",
    "supabaseUrl": "https://xyzcompany.supabase.co",
    "projectRef": "xyzcompany",
    "keyPreview": "sb_pub...9a4f",
    "status": "HEALTHY",
    "intervalHours": 12,
    "enabled": true,
    "lastAttemptedAt": "2026-10-14T18:00:00Z",
    "lastSuccessfulAt": "2026-10-14T18:00:00Z",
    "nextRunAt": "2026-10-15T06:00:00Z",
    "consecutiveFailures": 0,
    "lastLatencyMs": 142,
    "lastErrorCategory": null,
    "lastErrorMessage": null,
    "notes": "Main staging environment"
  }
]
```

---

### 2.2 POST `/api/v1/projects`
Creates a new project connection and initializes its background execution schedule.

**Request Body:**
```json
{
  "name": "E-Commerce Staging",
  "supabaseUrl": "https://xyzcompany.supabase.co",
  "publishableKey": "sb_publishable_abcdef1234567890",
  "intervalHours": 12,
  "notes": "Main staging environment"
}
```

**Response (201 Created):** Returns the created `ProjectResponse` object.

---

### 2.3 GET `/api/v1/projects/{id}`
Retrieves detailed operational and diagnostic data for a single project.

---

### 2.4 POST `/api/v1/projects/{id}/validate`
Performs a live, immediate database RPC verification against Supabase.

**Response (200 OK):**
```json
{
  "success": true,
  "httpStatusCode": 200,
  "latencyMs": 185,
  "errorCategory": null,
  "errorMessage": null,
  "responsePayload": "{\"ok\": true, \"server_time\": \"2026-10-14T18:15:00.123Z\"}"
}
```

---

### 2.5 POST `/api/v1/projects/{id}/run`
Triggers an immediate, manual "Run Now" heartbeat execution without waiting for the next cron schedule.

**Response (200 OK):**
```json
{
  "jobId": "e3b0c442-98fc-1c14-9afbf4c8996fb924",
  "status": "COMPLETED",
  "success": true,
  "latencyMs": 154,
  "errorCategory": null,
  "details": "Execution completed successfully"
}
```

---

### 2.6 POST `/api/v1/projects/{id}/pause` & `/resume`
Temporarily halts or resumes automated background heartbeat execution.

---

### 2.7 PUT `/api/v1/projects/{id}/key`
Rotates the stored Supabase Publishable Key.

**Request Body:**
```json
{
  "publishableKey": "sb_publishable_newsecretkey123"
}
```

---

### 2.8 DELETE `/api/v1/projects/{id}`
Deletes the project, its encrypted credentials, schedule, and historical executions.

---

## 3. Dashboard & History Endpoints

### 3.1 GET `/api/v1/dashboard/summary`
Returns aggregated real statistics for dashboard widgets.

**Response (200 OK):**
```json
{
  "totalProjects": 8,
  "healthyProjects": 7,
  "failedProjects": 1,
  "pausedProjects": 0,
  "totalExecutions24h": 32,
  "successfulExecutions24h": 31,
  "successRate24h": 96.88,
  "activeAlerts": 1,
  "workerHealthy": true,
  "lastWorkerHeartbeat": "2026-10-14T18:20:00Z"
}
```

---

### 3.2 GET `/api/v1/executions`
Returns paginated execution history with optional filtering.

**Query Parameters:**
- `page`: Page index (default: `0`).
- `size`: Items per page (default: `20`).
- `projectId`: Optional UUID filter.
- `status`: Optional filter (`SUCCESS`, `FAILED`, `TIMEOUT`).

---

### 3.3 GET `/api/v1/alerts`
Returns recent incident and resolution alerts.

### 3.4 POST `/api/v1/alerts/{id}/acknowledge`
Acknowledges an alert by the administrator.

---

## 4. Internal Worker API (Machine-to-Machine)

*Requires header `X-Worker-Token: <WAKEHARBOR_INTERNAL_TOKEN>`.*

### 4.1 POST `/api/internal/v1/jobs/claim`
Claims pending jobs using row-level database locks (`SKIP LOCKED`).

**Request Body:**
```json
{
  "workerId": "worker-node-alpha",
  "batchSize": 10
}
```

**Response (200 OK):**
```json
[
  {
    "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "projectId": "c1f7a0b3-e5d2-4a81-9b88-51f67f62e6e1",
    "projectName": "E-Commerce Staging",
    "supabaseUrl": "https://xyzcompany.supabase.co",
    "publishableKey": "sb_publishable_abcdef1234567890",
    "leaseToken": "7b9d1234-a82f-4100-9988-aabbccddeeff",
    "attemptCount": 0
  }
]
```

---

### 4.2 POST `/api/internal/v1/jobs/{id}/result`
Submits the execution outcome from the worker.

**Request Body:**
```json
{
  "leaseToken": "7b9d1234-a82f-4100-9988-aabbccddeeff",
  "success": true,
  "httpStatusCode": 200,
  "latencyMs": 142,
  "errorCategory": null,
  "errorMessage": null,
  "responsePayload": "{\"ok\": true, \"server_time\": \"2026-10-14T18:00:00.000Z\"}"
}
```

---

### 4.3 POST `/api/internal/v1/workers/heartbeat`
Transmits a liveness heartbeat from the background worker.

**Request Body:**
```json
{
  "workerId": "worker-node-alpha",
  "hostname": "vps-lon-01",
  "activeThreads": 2
}
```
