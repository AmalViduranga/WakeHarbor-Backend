# WakeHarbor — Operations Runbook & Troubleshooting

**Audience:** Site Reliability Engineers, System Administrators  
**Scope:** Operating, diagnosing, and resolving incidents in WakeHarbor  

---

## 1. Routine Operational Commands

### 1.1 Check Service Health
```bash
# Check container status
docker compose ps

# Check Control Service health via Actuator
curl -s http://127.0.0.1:8080/actuator/health | jq .

# Inspect live structured logs
docker compose logs -f --tail=100 control-service
docker compose logs -f --tail=100 heartbeat-worker
```

### 1.2 Inspect Active Database Leases
To view currently leased or pending jobs directly in PostgreSQL:
```bash
docker compose exec db psql -U wakeharbor_app -d wakeharbor -c "
SELECT id, project_id, status, lease_owner, lease_expires_at, attempt_count 
FROM execution_jobs 
WHERE status IN ('PENDING', 'LEASED') 
ORDER BY scheduled_time ASC;"
```

---

## 2. Troubleshooting Supabase Integration Incidents

### Incident 2.1: Status `AUTHENTICATION_ERROR` / HTTP 401
- **Symptoms:** Dashboard shows red badge `Authentication Error`, consecutive failures incrementing.
- **Root Cause:**
  - The publishable API key in WakeHarbor does not match the Supabase project.
  - The project's API credentials were reset in the Supabase dashboard.
- **Resolution:**
  1. Open the Supabase Project Dashboard -> **Settings** -> **API**.
  2. Locate the **Publishable Key** (starts with `sb_publishable_...`).
  3. In WakeHarbor, navigate to the project details page and click **Rotate Key**.
  4. Paste the new key and submit.
  5. Click **Run Now** to immediately verify recovery.

---

### Incident 2.2: Status `CONNECTION_FAILED` / HTTP 404 (`FUNCTION_NOT_FOUND`)
- **Symptoms:** Diagnostic shows `Missing SQL RPC function public.wakeharbor_heartbeat()`.
- **Root Cause:**
  - The read-only SQL function has not been created in the target Supabase project, or was dropped during a schema migration.
  - The function permissions were not granted to `anon`.
- **Resolution:**
  1. Open the Supabase **SQL Editor**.
  2. Run the canonical heartbeat script:
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

     REVOKE ALL ON FUNCTION public.wakeharbor_heartbeat() FROM PUBLIC, anon, authenticated;
     GRANT EXECUTE ON FUNCTION public.wakeharbor_heartbeat() TO anon;
     ```
  3. In WakeHarbor, click **Run Now** to verify.

---

### Incident 2.3: Status `SUPABASE_PAUSED` / HTTP 503 or 522
- **Symptoms:** Diagnostic indicates Supabase instance returned an inactivity pause response.
- **Root Cause:**
  - The 7-day inactivity threshold was exceeded before WakeHarbor began monitoring, and Supabase automatically paused the database instance.
- **Resolution:**
  1. WakeHarbor *cannot* unpause a project that is already in a hard paused state (only the Supabase dashboard owner can resume it).
  2. Log in to `https://supabase.com/dashboard`, locate the project, and click **Restore / Unpause**.
  3. Once Supabase displays **Active**, return to WakeHarbor and click **Run Now**. The status will automatically transition back to `HEALTHY`.

---

### Incident 2.4: Status `RATE_LIMITED` / HTTP 429
- **Symptoms:** High consecutive failures with HTTP 429 responses.
- **Root Cause:**
  - Outbound requests to the project exceed Supabase's API rate limits (rare with 12h intervals, possible if user spamming "Run Now").
- **Resolution:**
  - The worker automatically applies exponential backoff on HTTP 429.
  - Advise the administrator not to trigger repeated manual executions within short intervals.

---

## 3. Worker Telemetry & Failure Scenarios

### Scenario 3.1: Dashboard Alert "Background Worker Stale / Disconnected"
- **Symptoms:** Dashboard banner displays `Worker Disconnected: Heartbeat worker has not sent a liveness ping in > 3 minutes`.
- **Diagnosis:**
  ```bash
  docker compose ps heartbeat-worker
  docker compose logs --tail=100 heartbeat-worker
  ```
- **Resolution:**
  - If container crashed: `docker compose restart heartbeat-worker`.
  - If network partition: verify `CONTROL_SERVICE_URL` and `WAKEHARBOR_INTERNAL_TOKEN` match in both service environments.
  - Jobs leased to the dead worker will be automatically reclaimed by `JobLeasingService` within 5 minutes.
