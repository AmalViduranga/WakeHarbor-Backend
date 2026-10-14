# WakeHarbor — Production Deployment Guide

**Target Environment:** Single-node Linux VPS (Ubuntu 22.04 / 24.04 LTS or Debian 12)  
**Container Engine:** Docker Engine 24+ & Docker Compose v2+  
**Architecture:** Full-stack containerized microservices behind Nginx reverse proxy  

---

## 1. Production Architecture Overview

In production, all components are hosted on an always-on VPS to ensure background jobs execute continuously even when your browser is closed:

```text
[ Internet ]
      │
      ▼ (Port 80 / 443 - SSL Termination)
[ Nginx Reverse Proxy ]
      │
      ├──> /api/v1/*   ───> [ Control Service (Port 8080) ] ───> [ PostgreSQL (Port 5432) ]
      │                               ▲
      └──> /*          ───> [ Next.js Frontend (Port 3000) ]     │ (Private Docker Network)
                                                                 ▼
                            [ Heartbeat Worker (Autonomous) ] ───┘
```

**Security Rule:** PostgreSQL and the Heartbeat Worker are on an isolated internal Docker bridge network (`backend-net`). They are **never** exposed to the public internet.

---

## 2. Server Prerequisites

1. **Hardware Requirements:**
   - 1 vCPU (minimum), 2 vCPUs (recommended)
   - 2 GB RAM (minimum), 4 GB RAM (recommended)
   - 20 GB SSD storage
2. **Software Packages:**
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-plugin certbot python3-certbot-nginx
   sudo systemctl enable --now docker
   ```

---

## 3. Deployment Steps

### Step 1: Clone Repository & Create Directory Structure
```bash
mkdir -p /opt/wakeharbor
cd /opt/wakeharbor
git clone <YOUR_BACKEND_REPO_URL> backend
git clone <YOUR_FRONTEND_REPO_URL> frontend
```

### Step 2: Configure Production Environment Variables
Create `/opt/wakeharbor/.env` (permissions `chmod 600 /opt/wakeharbor/.env`):

```bash
# PostgreSQL
POSTGRES_DB=wakeharbor
POSTGRES_USER=wakeharbor_app
POSTGRES_PASSWORD=generate_a_strong_password_here

# Control Service
WAKEHARBOR_ADMIN_EMAIL=admin@yourdomain.com
WAKEHARBOR_ADMIN_PASSWORD=SetAStrongPassword123!
WAKEHARBOR_JWT_SECRET=generate_a_random_64_character_hex_string_here
WAKEHARBOR_ENCRYPTION_KEY=generate_a_random_32_character_encryption_key
WAKEHARBOR_INTERNAL_TOKEN=generate_a_random_machine_to_machine_token

# Worker
CONTROL_SERVICE_URL=http://control-service:8080

# SMTP (Optional - leave empty to run without external email)
SMTP_HOST=smtp.mailgun.org
SMTP_PORT=587
SMTP_USER=postmaster@yourdomain.com
SMTP_PASS=smtp_secret_pass
SMTP_FROM=alerts@yourdomain.com
```

### Step 3: Production Docker Compose Setup
Create `/opt/wakeharbor/docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  db:
    image: postgres:16-alpine
    restart: always
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - backend-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  control-service:
    build:
      context: ./backend
      dockerfile: infra/docker/Dockerfile.control
    restart: always
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      WAKEHARBOR_ADMIN_EMAIL: ${WAKEHARBOR_ADMIN_EMAIL}
      WAKEHARBOR_ADMIN_PASSWORD: ${WAKEHARBOR_ADMIN_PASSWORD}
      WAKEHARBOR_JWT_SECRET: ${WAKEHARBOR_JWT_SECRET}
      WAKEHARBOR_ENCRYPTION_KEY: ${WAKEHARBOR_ENCRYPTION_KEY}
      WAKEHARBOR_INTERNAL_TOKEN: ${WAKEHARBOR_INTERNAL_TOKEN}
    depends_on:
      db:
        condition: service_healthy
    networks:
      - backend-net
    ports:
      - "127.0.0.1:8080:8080"

  heartbeat-worker:
    build:
      context: ./backend
      dockerfile: infra/docker/Dockerfile.worker
    restart: always
    environment:
      CONTROL_SERVICE_URL: ${CONTROL_SERVICE_URL}
      WAKEHARBOR_INTERNAL_TOKEN: ${WAKEHARBOR_INTERNAL_TOKEN}
    depends_on:
      - control-service
    networks:
      - backend-net

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    restart: always
    environment:
      NEXT_PUBLIC_API_URL: https://wakeharbor.yourdomain.com
    networks:
      - backend-net
    ports:
      - "127.0.0.1:3000:3000"

networks:
  backend-net:
    driver: bridge

volumes:
  postgres_data:
```

### Step 4: Build and Start Containers
```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

---

## 4. Nginx Reverse Proxy & SSL Configuration

Create `/etc/nginx/sites-available/wakeharbor`:

```nginx
server {
    server_name wakeharbor.yourdomain.com;

    # Security Headers
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Backend API routing
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Frontend Next.js routing
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

Enable site and generate SSL certificate via Let's Encrypt:
```bash
sudo ln -s /etc/nginx/sites-available/wakeharbor /etc/nginx/sites-enabled/
sudo certbot --nginx -d wakeharbor.yourdomain.com
sudo systemctl reload nginx
```

---

## 5. Backup & Restoration

### Backup PostgreSQL Database
```bash
docker compose -f docker-compose.prod.yml exec -T db pg_dump -U wakeharbor_app wakeharbor | gzip > /opt/backups/wakeharbor_$(date +%Y%m%d_%H%M%S).sql.gz
```

### Restore Database
```bash
gunzip -c /opt/backups/wakeharbor_20261014_180000.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U wakeharbor_app -d wakeharbor
```
