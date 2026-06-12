# Key gen command
# Generate private key
openssl genrsa -out private.pem 2048

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem

3) https - feign api
   Step A: Create the Root CA (The Trust Anchor)
   # Generate the CA Private Key
   openssl genrsa -out rootCA.key 4096
   # Generate the Root Certificate (Valid for 10 years)
   openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 3650 -out rootCA.crt

   Step B: Generate Badminton Server Keys
   # Create Key & CSR (Certificate Signing Request)
   openssl genrsa -out bad20260227.key 2048
   openssl req -new -key bad20260227.key -out badminton.csr
   # Sign with Root CA
   openssl x509 -req -in badminton.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial -out badminton.crt -days 365 -sha256
   # Pack into PKCS12 (Java KeyStore format)
   openssl pkcs12 -export -in badminton.crt -inkey bad20260227.key -out badminton.p12 -name "badminton"

   Step C: Generate Authorization Server Keys
   Repeat Step B, but replace "badminton" with "auth-server".

   Step D: Create the Truststore
   This file tells the servers to trust anyone signed by your rootCA.crt.
   keytool -import -file rootCA.crt -alias myCA -keystore truststore.jks

20260228: complete the microservice authentication.
    Flows: ![Flow diagram: Badminton-court-management system authenticate via publiclink-app system](img/communicate-micro-authen-service.drawio.svg)

---

## Data Backup Service (`data-backup`)

Independent Spring Boot 3.5.0 microservice (port 9090) for scheduled and on-demand backups of `publiclink-db` and `user_schema` databases.

### Features
- **Scheduled backups**: Daily (midnight), weekly (Monday), monthly (1st) via configurable cron
- **Manual triggers**: Via REST API or RabbitMQ message
- **Incremental backup**: Watermark-based change detection (auto-increment ID + timestamp comparison)
- **Full backup**: Complete snapshot of all tables
- **OTP authentication**: 2-minute TTL OTP sent to admin email before backup session is established
- **Email summary**: HTML report sent to `jelly1512@proton.me` after each trigger
- **GZIP-compressed JSON** backup files stored on local filesystem

### Architecture
```
[Admin] --> [publiclink-app :9080] --(SMTP)--> jelly1512@proton.me (OTP)
                ^
[Admin] --> [data-backup :9090] --(verify OTP)--> publiclink-app
                |
       +--------+--------+
       |                 |
[publiclink-db]   [user_schema]
 (read-only)      (read-only)
       |                 |
       +--------+--------+
                |
    [Local filesystem backup]
    /backups/{schema}/{date}/
```

### API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/token` | Authenticate with OTP `{sessionId, otpCode}` |
| POST | `/api/backup/trigger` | Manual trigger `{scope, schemas[]}` |
| GET | `/api/backup/history` | Paginated trigger history |
| GET | `/api/backup/history/{id}` | Trigger detail |
| GET | `/api/backup/files/{triggerId}` | Backup files for a trigger |
| DELETE | `/api/backup/files/{fileId}` | Delete a backup file |
| GET | `/api/backup/watermarks` | Current watermark state |

### OTP Flow
1. Admin calls `publiclink-app` POST `/api/backup/otp/generate` -> OTP sent to email, returns `sessionId`
2. Admin reads OTP from email, calls `data-backup` POST `/api/auth/token` with `{sessionId, otpCode}`
3. `data-backup` verifies OTP via `publiclink-app` within 2-min window, receives backup auth token
4. Token is used in `X-Backup-Token` header for all subsequent backup API calls (5-min TTL)

### Configuration Profiles
- `application-dev.properties` — localhost connections, local backup path (`./backups`)
- `application-prod.properties` — environment variable-based configuration

### Database Schemas
- **backup_meta**: `trigger_tbl`, `backup_file_tbl`, `otp_session_tbl`, `backup_watermark_tbl`
- **publiclink-db**: adds `backup_otp` table (via changeset-018)

### RabbitMQ
- Exchange: `backup.exchange` (topic)
- Queue: `backup.trigger.queue` — receives manual trigger commands
- Queue: `backup.status.queue` — publishes completion/failure events

    