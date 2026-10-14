# WakeHarbor — Security & Threat Modeling

**Classification:** Application Security Architecture  
**Document Version:** 1.0  

---

## 1. Threat Model & Mitigations

| Threat Vector | Risk Level | Architectural Mitigation |
|---|---|---|
| **Server-Side Request Forgery (SSRF)** | **Critical** | Hostname strictly constrained to `*.supabase.co`. Custom DNS pre-resolution blocks private IP addresses, loopback (`127.0.0.0/8`), link-local (`169.254.0.0/16`), and AWS metadata endpoints (`169.254.169.254`). HTTP redirects explicitly disabled. Client cannot supply custom endpoint paths. |
| **Credential Theft at Rest** | **High** | API keys encrypted using authenticated AES-256-GCM with PBKDF2/SHA-256 derived keys, 12-byte random initialization vectors, and 128-bit authentication tags. Stored in isolated `project_credentials` table. |
| **Credential Leakage in Logs/UI** | **High** | Zero credential logging. Outbound HTTP requests strip API keys from log interceptors. Frontend responses only receive a masked preview (`sb_pub...9a4f`). |
| **Unauthorized Background Access** | **High** | Internal worker APIs require an immutable `X-Worker-Token` header validated with constant-time equality checks. Not exposed publicly through the reverse proxy. |
| **Session Hijacking / CSRF** | **Medium** | Authentication tokens issued in `HttpOnly`, `SameSite=Lax`, and `Secure` cookies. State-changing requests validated server-side. |
| **Brute Force Login** | **Medium** | In-memory sliding-window rate limiter limits authentication attempts to 5 per minute per IP address. Passwords hashed using BCrypt (strength 12). |
| **Race Conditions in Job Claiming** | **Medium** | Relational transactions with row-level locks (`SELECT ... FOR UPDATE SKIP LOCKED`) ensure jobs are leased to exactly one worker node. |

---

## 2. Server-Side Request Forgery (SSRF) Defense

Because WakeHarbor executes outbound HTTP requests against external endpoints based on user input, rigorous SSRF defense is implemented in `SsrfValidator.java`:

### 2.1 URL Parsing & Hostname Whitelisting
1. **Scheme Validation:** Only `https://` is permitted. `http://`, `file://`, `ftp://`, and all other protocols are rejected.
2. **Path Sanitization:** User-supplied paths are discarded. The backend always appends the immutable path `/rest/v1/rpc/wakeharbor_heartbeat`.
3. **Domain Pattern Matching:** Hostname must match the regex `^[a-z0-9_-]+(\.supabase\.(co|in))$`.
4. **Disallowed Constructs:** Any URL containing userinfo (`user:pass@host`), query strings (`?foo=bar`), non-standard ports, or IP literals is immediately rejected.

### 2.2 DNS Pre-Resolution & IP Filtering
Before an HTTP connection is established:
1. All IP addresses for the hostname are resolved via `InetAddress.getAllByName(hostname)`.
2. Each address is checked against:
   - Loopback addresses (`127.0.0.0/8`, `::1`)
   - RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
   - Link-local and cloud metadata addresses (`169.254.0.0/16`, `fe80::/10`)
   - Broadcast and multicast addresses
3. If **any** resolved IP address violates these rules, the request is aborted with an `SSRF_BLOCKED` diagnostic error.

### 2.3 Redirect Disabling
Java 21 `HttpClient` is configured with `HttpClient.Redirect.NEVER`. Even if an upstream endpoint returns a 301/302 redirect to an internal IP address or metadata endpoint, the client does not follow it.

---

## 3. Cryptography & Key Management

### 3.1 Encryption Algorithm
- **Cipher:** `AES/GCM/NoPadding`
- **Key Length:** 256 bits (derived from `WAKEHARBOR_ENCRYPTION_KEY` using SHA-256)
- **Initialization Vector (IV):** 12 bytes generated per encryption operation via `SecureRandom`
- **Authentication Tag:** 128 bits appended to the ciphertext to ensure integrity and prevent tampering
- **Version Tag:** `key_version` column in `project_credentials` allows future key rotation without downtime

### 3.2 Key Rotation Protocol
The administrator can rotate an encryption key by updating `WAKEHARBOR_ENCRYPTION_KEY` and running a migration script, or rotating individual Supabase keys via the UI's **Rotate Key** modal.

---

## 4. Administrative Security

- **Bootstrap Seeding:** Admin credentials are initialized once during the initial application startup from environment variables (`WAKEHARBOR_ADMIN_EMAIL`, `WAKEHARBOR_ADMIN_PASSWORD`).
- **Single-Admin Enforcement:** The system contains exactly one active administrator account in the MVP. Public user registration is disabled at the controller and service layers.
- **Password Strength:** Passwords must meet minimum complexity (at least 8 characters).
