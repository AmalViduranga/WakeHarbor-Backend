# WakeHarbor — Backend

**Keep your development projects active, intelligently.**

WakeHarbor Backend is a robust, production-minded Java 21 / Spring Boot platform for scheduling lightweight database activity against Supabase Free Plan projects, recording verified execution outcomes, detecting failures, and monitoring connections.

---

## Architecture Overview

WakeHarbor Backend consists of two independently deployable Spring Boot microservices:

```text
wakeharbor-backend/
├── services/
│   ├── control-service/     # Port 8080: Admin auth, projects, scheduling, leasing, REST API
│   └── heartbeat-worker/    # Port 8081: Autonomous execution engine, SSRF guards, RPC dispatch
├── infra/
│   └── docker/              # Dockerfiles and Docker Compose configuration
├── docs/                    # Architectural and operational documentation
│   ├── product-requirements.md
│   ├── architecture.md
│   ├── api.md
│   ├── database.md
│   ├── security.md
│   ├── deployment.md
│   └── runbook.md
├── pom.xml                  # Parent Maven multi-module POM
├── .env.example             # Template environment variables
└── README.md
```

### 1. Control Service (`services/control-service`)
- Manages single-admin authentication (BCrypt, rate limiting, HttpOnly session cookie).
- Handles project CRUD, URL canonicalization, and AES-256-GCM credential encryption.
- Orchestrates persistent background scheduling and atomic job leasing via PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED`.
- Reclaims expired job leases automatically upon worker crash.
- Emits deduplicated incident and recovery alerts.
- Only this service connects to the PostgreSQL database.

### 2. Heartbeat Worker (`services/heartbeat-worker`)
- Autonomous polling worker with zero database access.
- Claims leased jobs and reports results via authenticated internal HTTP endpoints (`X-Worker-Token`).
- Validates external Supabase hostnames using custom DNS pre-resolution to prohibit SSRF to private/loopback networks.
- Dispatches read-only database RPC calls to `public.wakeharbor_heartbeat()`.
- Emits continuous liveness telemetry for heartbeat worker monitoring.

---

## Prerequisites

- **Java Development Kit (JDK):** Version 21 LTS or newer.
- **Apache Maven:** Version 3.9+.
- **PostgreSQL:** Version 15+ (or use Docker).
- **Docker & Docker Compose:** (Optional, for containerized running).

---

## Quick Start (Local Development)

### 1. Start PostgreSQL Database
```powershell
# Using Docker
docker run --name wakeharbor-db -e POSTGRES_DB=wakeharbor -e POSTGRES_USER=wakeharbor_app -e POSTGRES_PASSWORD=devpassword -p 5432:5432 -d postgres:16-alpine
```

### 2. Configure Environment Variables
Copy `.env.example` to `.env`:
```powershell
cp .env.example .env
```
Ensure `WAKEHARBOR_ADMIN_EMAIL`, `WAKEHARBOR_ADMIN_PASSWORD`, `WAKEHARBOR_JWT_SECRET`, and `WAKEHARBOR_ENCRYPTION_KEY` are configured.

### 3. Run Automated Tests
```powershell
mvn clean test
```
All 26 unit and integration tests will execute and pass without requiring real Supabase credentials.

### 4. Run the Control Service
```powershell
cd services/control-service
mvn spring-boot:run
```
Flyway will automatically create and migrate the 11 database tables, seed the bootstrap administrator account, and start the HTTP server on `http://localhost:8080`.

### 5. Run the Heartbeat Worker
In a second terminal window:
```powershell
cd services/heartbeat-worker
mvn spring-boot:run
```
The worker will connect to `http://localhost:8080`, transmit a liveness ping, and begin polling for scheduled heartbeat jobs.

---

## Running with Docker Compose

To start the entire backend stack in isolated containers:
```powershell
docker compose -f infra/docker/docker-compose.yml up --build -d
```

---

## Documentation

Full architectural specifications are located in the `docs/` directory:
- [Product Requirements](docs/product-requirements.md)
- [System Architecture](docs/architecture.md)
- [REST API Specification](docs/api.md)
- [Database Schema & ERD](docs/database.md)
- [Security & SSRF Mitigation](docs/security.md)
- [Production VPS Deployment](docs/deployment.md)
- [Operations Runbook](docs/runbook.md)
