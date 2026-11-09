# Deploying share-link.top (PublicLink)

Fresh, verified setup for the production server (Kali Linux VM, fronted by a Cloudflare Tunnel).
The VM/network/tunnel side is documented in the HTML guide
`hosting-windows10-virtualbox-kali.html`; this file covers the **application side**: the
one-time `setup-server.sh` provisioning and the per-release `deploy.sh` workflow.

```
Internet  →  Cloudflare edge  →  cloudflared tunnel  →  nginx :80 (tunnel target)
nginx:  /apiGateway/  →  publiclink-gateway  127.0.0.1:8080  (java -jar apiGateway.war)
        everything else →  Tomcat             127.0.0.1:8081  (React SPA in webapps/ROOT)
gateway routes:  /publiclink/**  → publiclink-app  :9080  (java -jar publiclink-exec.jar)
                 /api/v1/user/** → publiclink-user :8082  (java -jar user-0.0.1-SNAPSHOT.jar)
                 /chat/**        → publiclink-chat :9090  (java -jar chat-exec.jar)
support:  MariaDB :3306 (publiclink-db, user_schema, chat_schema) · Redis :6379 (gateway rate limiter)
          RabbitMQ :5672 (gateway events)
```

| Port | Service | Bound to | Runs as | Artifact |
|------|---------|----------|---------|----------|
| 80   | nginx   | 0.0.0.0  | root      | `deploy/nginx/share-link.top.tunnel.conf` (port 80 only, no TLS) |
| 8080 | publiclink-gateway  | 127.0.0.1 | publiclink | `/opt/publiclink/apiGateway.war` |
| 8081 | tomcat              | 127.0.0.1 | tomcat     | React build in `webapps/ROOT` |
| 9080 | publiclink-app      | 127.0.0.1 | publiclink | `/opt/publiclink/publiclink-exec.jar` |
| 8082 | publiclink-user     | 127.0.0.1 | publiclink | `/opt/publiclink/user-0.0.1-SNAPSHOT.jar` |
| 9090 | publiclink-chat     | 127.0.0.1 | publiclink | `/opt/publiclink/chat-exec.jar` (Zalo chat bridge) |
| 3306 | mariadb             | 127.0.0.1 | mysql      | `publiclink-db` + `user_schema` + `chat_schema` |
| 6379 | redis-server        | 127.0.0.1 | redis      | gateway rate limiter (required — see troubleshooting) |
| 5672 | rabbitmq-server     | 127.0.0.1 | rabbitmq   | gateway event publishing |

---

## 1. Prerequisites (on the server)

| Tool | Version | Notes |
|------|---------|-------|
| JDK   | 25 | JARs are bytecode 69; `JAVA_HOME` must point at JDK 25 — both `deploy.sh` and `build-and-deploy.sh` auto-detect `/usr/lib/jvm/java-25-*` when unset |
| Maven | any recent | |
| Node.js | ≥ 18 | + npm, for the frontend build |
| git + SSH key | | key registered on GitHub (`git@github.com:Vissel/…`) |
| Tomcat 11 | `/opt/tomcat` | serves ONLY the React SPA from `webapps/ROOT` on `127.0.0.1:8081` |
| nginx | | tunnel-mode config, port 80 only |
| MariaDB, Redis, RabbitMQ | | see Step 1 below |
| cloudflared | | tunnel `share-link`, ingress → `http://localhost:80` |

---

## 2. One-time server setup — `setup-server.sh`

Run once (as `sudo`) from a fresh clone. The script is idempotent:

```bash
cd ~/deployment/publiclink-workspace/back-end-publiclink
chmod +x setup-server.sh
sudo ./setup-server.sh
```

What it installs:

| Step | What it does |
|------|--------------|
| 1  | Moves Tomcat's HTTP connector to `port="8081"` with exactly ONE `address="127.0.0.1"` (perl slurp edit strips every pre-existing address attribute first — duplicate attributes make Tomcat refuse to start with a `SAXParseException`) |
| 1c | Installs the `RewriteValve` + `rewrite.config` so SPA deep routes (`/login`, …) answer **200** instead of a 404-bodied index.html |
| 1d | Installs and enables `redis-server` and `rabbitmq-server` |
| 1b | Creates the `publiclink` system account (units run as `User=publiclink`; without it every start fails with `status=217/USER`) |
| 2  | Copies RSA keys (`auth260222.pem`, `pubauth.pem`) to `/opt/publiclink/keys/`, `chmod 600` + owned by `publiclink` |
| 3+ | Installs `/etc/publiclink/publiclink.env`, the systemd units (gateway, app, user, **chat**) and the nginx config; enables/starts nginx and the four services |

### 2.1 After the script — manual steps the script does NOT do

**a) Fill the env file** (`/etc/publiclink/publiclink.env`, installed with empty secrets):

```bash
sudo nano /etc/publiclink/publiclink.env
```

- `JWT_KEY` and `JWT_URLKEY` — two different secrets, 64 hex chars each:
  ```bash
  openssl rand -hex 32   # run twice; keep both values forever — regenerating
                         # invalidates all issued tokens and sale-link URLs
  ```
  (`JWT_KEY` → `jwt.secret`, `JWT_URLKEY` → `jwt.secret.url`; gateway and app read the same file, so values must match across services.)
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` and `USER_DB_URL` / `USER_DB_USERNAME` / `USER_DB_PASSWORD` — see 2.2. Use sed with `|` delimiters (the JDBC URLs contain `/`):
  ```bash
  sudo sed -i 's|^DB_PASSWORD=.*|DB_PASSWORD=yourpass|' /etc/publiclink/publiclink.env
  ```
  Passwords containing `|`, `&` or `\` must be edited with `nano` instead.
- `CHAT_DB_URL` / `CHAT_DB_USERNAME` / `CHAT_DB_PASSWORD` — chat-app's own schema (`chat_schema`).
  **CHAT_** prefixed on purpose: `DB_*` is publiclink-app's and `USER_DB_*` is the user service's, and
  all three read this one EnvironmentFile.
- `ZALO_*` — chat bridge credentials. Leave `ZALO_ENABLED=false` here; section 2.2 turns it on once
  the Zalo side exists. Nothing secret about the schema: the tables are created by chat-app itself.

**b) Provision MariaDB** (Kali package is `default-mysql-server` = MariaDB):

```sql
CREATE DATABASE IF NOT EXISTS `publiclink-db`;
CREATE USER IF NOT EXISTS 'publiclink-user'@'localhost' IDENTIFIED BY 'publiclink_pass01';
GRANT ALL PRIVILEGES ON `publiclink-db`.* TO 'publiclink-user'@'localhost';
CREATE DATABASE IF NOT EXISTS `user_schema`;
CREATE USER IF NOT EXISTS 'user-service-user'@'localhost' IDENTIFIED BY 'user_service_pass03';
GRANT ALL PRIVILEGES ON `user_schema`.* TO 'user-service-user'@'localhost';
CREATE DATABASE IF NOT EXISTS `chat_schema` CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'chat-service-user'@'localhost' IDENTIFIED BY 'chat_service_pass04';
GRANT ALL PRIVILEGES ON `chat_schema`.* TO 'chat-service-user'@'localhost';
FLUSH PRIVILEGES;
```

Liquibase creates the schema on first boot; an empty database is fine.

**c) Cloudflare Tunnel** — install per the HTML guide Part 8, with the ingress pointing at
**nginx on port 80**, never at the gateway/Tomcat ports directly:

```yaml
ingress:
  - hostname: share-link.top
    service: http://localhost:80
  - service: http_status:404
```

⚠ The systemd unit reads **`/etc/cloudflared/config.yml`** (check `ps aux | grep cloudflared`), not
`~/.cloudflared/config.yml`. After any change, sync BOTH files and restart. Set Cloudflare
*SSL/TLS → Full*.

**d) Enable everything** and verify boot resilience:

```bash
sudo systemctl enable --now mariadb redis-server rabbitmq-server tomcat nginx cloudflared \
     publiclink-gateway publiclink-app publiclink-user publiclink-chat
sudo ss -tlnp | grep -E ':(80|8080|8081|9080|8082|9090|3306|6379|5672)'
# expected: :80 on 0.0.0.0, everything else bound to 127.0.0.1
```

> Do NOT re-run `setup-server.sh` on a live server whose nginx config has been hand-fixed to the
> tunnel variant — it would reinstall the old 443/Let's Encrypt config and break nginx again.

### 2.2 First-time bring-up of the chat package (chat-app)

One-time, after 2.1. The chat bridge is a separate deployable on **:9090** with its own
schema, so it is deployed independently of the others — and releasing `publiclink-app` no longer
drops open chat streams.

**a) Create its schema** (chat-app owns `chat_schema` alone; publiclink-app stays the only DDL
owner of `publiclink-db`). Liquibase creates the tables on first boot, the database must exist:

```sql
CREATE DATABASE IF NOT EXISTS `chat_schema` CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'chat-service-user'@'localhost' IDENTIFIED BY 'chat_service_pass04';
GRANT ALL PRIVILEGES ON `chat_schema`.* TO 'chat-service-user'@'localhost';
FLUSH PRIVILEGES;
```

**b) Build and stage** (from the repo root — `chat-app` is in the reactor, so a root build covers it):

```bash
mvn clean package -DskipTests -Pprod          # → chat-app/target/chat-exec.jar
# or via the release script, which stages all four artifacts to target/deploy/:
./build-and-deploy.sh /opt/tomcat/webapps prod
```

**c) Install the unit and the jar, then start it** (order matters: the jar must exist before the
unit starts, or systemd's `Restart=on-failure` loop hides a missing file as a crash loop):

```bash
cd ~/deployment/publiclink-workspace/back-end-publiclink
sudo cp deploy/systemd/publiclink-chat.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo cp target/deploy/chat-exec.jar /opt/publiclink/
sudo chown -R publiclink:publiclink /opt/publiclink
sudo systemctl enable --now publiclink-chat
```

**d) Update nginx** — the chat event stream needs its own location (buffering off, long read
timeout), otherwise every stream is cut at 120 s. Copy the versioned config and reload, never restart:

```bash
sudo cp deploy/nginx/share-link.top.tunnel.conf /etc/nginx/conf.d/share-link.top.conf
sudo nginx -t && sudo systemctl reload nginx
```

**e) Verify it is reachable before touching Zalo** — local, then through the whole chain:

```bash
curl -s http://127.0.0.1:9090/chat/api/v1/chat/health                       # {"status":"UP",…}
curl -s https://share-link.top/apiGateway/chat/api/v1/chat/health           # same, via nginx+gateway
sudo journalctl -u publiclink-chat -n 50 --no-pager | grep -i "Running Changeset"   # chat tables created
```

If the local curl works and the public one does not, the fault is the gateway `/chat/**` route or
the permit-all list — not chat-app.

**f) Register the Zalo side under the `/chat` namespace.** In `developers.zalo.me`, on the Zalo App:

| Setting | Value |
|---|---|
| OAuth Callback URL | `https://share-link.top/apiGateway/chat/api/v1/chat/zalo/consent/callback` |
| Webhook URL | `https://share-link.top/apiGateway/chat/api/v1/chat/zalo/webhook` |
| Events to tick | `user_send_text`, `user_received_message`, `user_seen_message`, `follow`, `unfollow` |

Then copy the **OA Secret Key** shown on the Webhook page into `ZALO_OA_SECRET_KEY` — it is the only
gate on that public endpoint. Zalo verifies URL ownership with a `zalo_verifier<code>.html` file: put
its contents in `ZALO_DOMAIN_VERIFICATION_CODE`; chat-app serves it under the `/chat` prefix. A
mismatched callback URL answers `-14003 Invalid redirect uri`.

**g) Turn the bridge on, in this order:**

```bash
# 1. fill ZALO_APP_ID, ZALO_SECRET_KEY, ZALO_OA_SECRET_KEY, ZALO_OA_ID, the redirect URI
sudo systemctl restart publiclink-chat
# 2. run the one-time OA consent: POST …/chat/admin/v1/chat/zalo/consent/begin (as Admin) and
#    open the returned URL in the OA administrator's browser
# 3. bind the administrator: from their personal Zalo, send the OA one message "LINK-ADMIN"
# 4. switch on
sudo sed -i 's|^ZALO_ENABLED=.*|ZALO_ENABLED=true|' /etc/publiclink/publiclink.env
sudo systemctl restart publiclink-chat
# 5. prove the round trip: open the chat widget, send a message, watch it arrive in Zalo,
#    answer with Reply (quote), and confirm the status ticks move sent → delivered → seen
sudo journalctl -u publiclink-chat -f
```

Until step 4 the feature still works **console-only** (`zalo.enabled=false`): visitors chat, staff
answer in `/adminChat`, and nothing is sent to Zalo. That is the intended safe default.

---

## 3. Release deployment — `deploy.sh`

Run on the server for every release:

```bash
cd ~/deployment/publiclink-workspace/back-end-publiclink
./deploy.sh <github_tag> /opt/tomcat
# e.g. ./deploy.sh v1.2.3 /opt/tomcat   — `main`/`develop` clone branches, anything else = tag
```

What it does, in order:

1. **Step 0 — pre-flight:** git, SSH keys in `~/.ssh`, JDK 25 (`JAVA_HOME` auto-detected), Maven, Node ≥ 18, npm.
2. **Step 1 — clone:** `rm -rf` then fresh `git clone --branch <tag>` of `back-end-publiclink` and
   `frontend-public-link` into the script's directory. ⚠ local uncommitted changes under those two
   directories are destroyed — commit or move them first.
3. **Step 2 — build & deploy frontend:** runs `build-and-deploy.sh /opt/tomcat/webapps prod`, which
   builds backend (adapter, apiGateway, publiclink-app, user, **chat-app**) + frontend and replaces
   `webapps/ROOT` with the fresh React build. Backend artifacts — including `chat-exec.jar` — are
   only **staged** to `target/deploy/` (see 3.1).
4. **Step 3 — health checks:** `https://share-link.top/login` must return **200**,
   `https://share-link.top/apiGateway/api/v1/auth/public-key` must return a **non-empty, non-204**
   PEM body, and `https://share-link.top/apiGateway/chat/api/v1/chat/health` must return **200 with
   `"status":"UP"`** (this third one covers nginx + the `/chat/**` gateway route + chat-app at once).

`set -euo pipefail` is active: any failing step aborts the script (and later steps do not run).
Note the frontend wipe happens **before** the staging copy — if the frontend copy fails, the old
ROOT is already deleted and the backend was never staged. Fix the cause and re-run; the fresh
build still exists in `frontend-public-link/build`.

### 3.1 Copy the staged backend artifacts (manual step)

`build-and-deploy.sh` stages the JARs but does not copy them into `/opt/publiclink`. The chat
service is released independently — restarting it drops every open chat stream, but restarting
`publiclink-app` no longer does:

```bash
cd ~/deployment/publiclink-workspace/back-end-publiclink
sudo cp target/deploy/apiGateway.war              /opt/publiclink/
sudo cp target/deploy/publiclink-exec.jar         /opt/publiclink/
sudo cp target/deploy/user-0.0.1-SNAPSHOT.jar     /opt/publiclink/
sudo cp target/deploy/chat-exec.jar               /opt/publiclink/
sudo chown -R publiclink:publiclink /opt/publiclink
sudo systemctl restart publiclink-gateway publiclink-app publiclink-user publiclink-chat
```

### 3.2 Final verification

```bash
curl -I http://127.0.0.1:8081/login                                  # 200
curl -s http://127.0.0.1:8080/api/v1/auth/public-key                 # PEM block
curl -I https://share-link.top/login                                 # HTTP/2 200
```

---

## 4. Troubleshooting (all verified in production)

| Symptom | Root cause | Fix |
|---------|-----------|-----|
| 502 with `server: cloudflare` | origin chain dead (tunnel fine) — check listeners first | `ss -tlnp`: :80, :8080, :8081 must listen; ingress must target `http://localhost:80` |
| 502 with `server: nginx` | nginx up, upstream down | `systemctl status publiclink-gateway publiclink-app publiclink-user publiclink-chat` |
| `code=exited, status=217/USER` on the services | `publiclink` account missing | `sudo useradd --system --no-create-home --shell /usr/sbin/nologin publiclink` + `sudo chown -R publiclink:publiclink /opt/publiclink`, restart units |
| Tomcat loop: `Main process exited, status=1` | duplicate `address` attribute in `server.xml` Connector | `tail -40 /opt/tomcat/logs/catalina.out`; keep exactly one `address="127.0.0.1"` |
| `nginx -t` fails: cannot load `/etc/letsencrypt/live/…` | HTTPS config installed but no cert — wrong for tunnel | install `deploy/nginx/share-link.top.tunnel.conf` (port 80 only), symlink into sites-enabled, restart nginx |
| public-key returns `AUTH_001 "Invalid username or password"` | Redis down — the rate limiter runs on every request and the exception handler mislabels non-business errors as AUTH_001 | `redis-cli ping` → `PONG`; `sudo systemctl enable --now redis-server`; restart gateway |
| public-key returns 204 / empty | RSA keys missing or unreadable | check `/opt/publiclink/keys/` exists, owned by `publiclink`, `chmod 600` |
| `/login` → 404 while `/` → 200 | SPA fallback keeps the 404 status (web.xml error page) | RewriteValve installed by `setup-server.sh` Step 1c; browsers render fine either way |
| cloudflared active but ERR spam | ingress points at a dead port | set `service: http://localhost:80` in BOTH `/etc/cloudflared/config.yml` and `~/.cloudflared/config.yml`, restart |
| edited tunnel config, nothing changed | unit reads `/etc/cloudflared/config.yml`, not `~/.cloudflared/` | `sudo cp ~/.cloudflared/config.yml /etc/cloudflared/` |
| `release version 25 not supported` during build | Maven uses an older JDK from `JAVA_HOME` | export `JAVA_HOME=/usr/lib/jvm/java-25-…` (scripts auto-detect it) |
| MariaDB `ERROR 1064` on `IDENTIFIED WITH … BY` | MariaDB 11 does not accept MySQL-style plugin syntax | use plain `CREATE USER … IDENTIFIED BY 'password'` |
| `chat/health` answers 404 but `127.0.0.1:9090/chat/...` answers 200 | gateway has no `/chat/**` route, or the chat route is shadowed by `/publiclink/**` | check `GatewayRoutesConfig` and restart `publiclink-gateway` after changing it |
| `chat/health` answers 502 | chat-app not listening | `systemctl status publiclink-chat`; `journalctl -u publiclink-chat -n 50` (a missing `/opt/publiclink/chat-exec.jar` shows as a restart loop) |
| Chat tables missing / `Table 'chat_schema.chat_message' doesn't exist` | `chat_schema` was never created, so Liquibase had nowhere to write | run the SQL in 2.2(a), then `systemctl restart publiclink-chat` and look for `Running Changeset … 001` |
| Visitors see "Sending" forever, nothing reaches Zalo | `ZALO_ENABLED=false`, no OA token, or no administrator bound | `curl …/chat/health` shows `zaloEnabled`; `GET /chat/admin/v1/chat/zalo/status` shows token + adminBound |
| Zalo webhook answered 401 / no events arrive | `ZALO_OA_SECRET_KEY` missing or wrong, or the registered URL prefix changed to `/chat` | set the OA Secret Key from the Webhook page; re-verify `zalo_verifier<code>.html` via `ZALO_DOMAIN_VERIFICATION_CODE`; check `journalctl -u publiclink-chat` for "non-matching signature" |
| Chat status never reaches "Delivered/Seen" | Zalo event types not ticked on the Webhook page, or the admin answered in oa.zalo.me rather than the OA thread | tick `user_received_message` + `user_seen_message`; both sides of a mirror reply are recorded either way |

### 4.1 Public site 502 (`server: cloudflare`) while everything works locally

**Situation** (verified 8 Nov 2026):

```
curl -I http://127.0.0.1/login        -> HTTP/1.1 404, Server: nginx   (nginx + Tomcat alive)
curl -I https://share-link.top/login  -> HTTP/2 502, server: cloudflare, cf-ray: …
```

A `server: cloudflare` 502 means Cloudflare's edge cannot reach the origin through the tunnel.
Since local curls answer, the fault is `cloudflared` on the VM, not nginx/Tomcat.

**Fix:**

```bash
# 1. Is the tunnel service alive, and enabled across reboots?
sudo systemctl status cloudflared --no-pager
systemctl is-enabled cloudflared          # must say enabled, or the site dies on every reboot

# 2. Logs — look for "Registered tunnel connection" vs repeated ERR lines
sudo journalctl -u cloudflared -n 40 --no-pager

# 3. The ingress the RUNNING unit reads must target nginx on :80
sudo cat /etc/cloudflared/config.yml
#    expected: hostname: share-link.top -> service: http://localhost:80

# 4. Restart and watch it register
sudo systemctl restart cloudflared
sudo journalctl -u cloudflared -f
```

Then re-check: `curl -I https://share-link.top/login` must stop returning 502.
Note: the systemd unit reads `/etc/cloudflared/config.yml` (verify with
`ps aux | grep cloudflared`), NOT `~/.cloudflared/config.yml` — keep both copies in sync.

### 4.2 `/login` returns 404 with `server: nginx` (RewriteValve missing)

**Situation** (verified 8 Nov 2026, after a MANUAL frontend copy — `setup-server.sh` Step 1c was skipped):

```
curl -I http://127.0.0.1/login                  -> HTTP/1.1 404, Content-Length: 588
                                                   (index.html body served by the WEB-INF/web.xml
                                                    error page, which KEEPS the 404 status)
grep RewriteValve /opt/tomcat/conf/server.xml   -> no output (Valve never installed)
cat /opt/tomcat/conf/Catalina/localhost/rewrite.config
  -> without sudo: Permission denied; with sudo: No such file or directory
```

**Fix:**

```bash
# 1. Create the rewrite config (root-owned path — use sudo tee)
sudo mkdir -p /opt/tomcat/conf/Catalina/localhost
sudo tee /opt/tomcat/conf/Catalina/localhost/rewrite.config > /dev/null <<'EOF'
# SPA fallback: rewrite deep routes to /index.html with a 200 status.
RewriteCond %{REQUEST_URI} !^/static/
RewriteCond %{REQUEST_FILENAME} !-f
RewriteRule ^ /index.html [L]
EOF

# 2. Sanity-check the anchor BEFORE inserting — expect exactly ONE </Host> line
#    (if more than one, stop and inspect — do not run the sed)
grep -n '</Host>' /opt/tomcat/conf/server.xml

# 3. Insert the Valve right before </Host>. Never touch the <Connector> line:
#    a bad sed there creates the fatal duplicate-address Tomcat crash loop.
sudo sed -i '/<\/Host>/i\      <Valve className="org.apache.catalina.valves.rewrite.RewriteValve" />' /opt/tomcat/conf/server.xml

# 4. Verify the edit landed, restart, and prove it end-to-end
grep -n 'RewriteValve' /opt/tomcat/conf/server.xml   # must print the Valve line
sudo systemctl restart tomcat
curl -I http://127.0.0.1:8081/login    # 200
curl -I http://127.0.0.1/login         # 200
curl -I https://share-link.top/login   # HTTP/2 200
```

If Tomcat fails to start after the sed, the XML was mangled — inspect
`sudo tail -40 /opt/tomcat/logs/catalina.out`.

---

## 5. Key files

| File | Purpose |
|------|---------|
| `deploy.sh` | per-release: clone → build → deploy frontend → health checks |
| `setup-server.sh` | one-time provisioning (Tomcat connector, RewriteValve, user, keys, env, units, nginx, Redis/RabbitMQ) |
| `build-and-deploy.sh` | backend + frontend build, `webapps/ROOT` replacement, artifact staging |
| `deploy/nginx/share-link.top.tunnel.conf` | nginx tunnel-mode config (port 80, prefix stripping, cache headers) |
| `deploy/systemd/*.service` | gateway/app/user/chat units (User=publiclink, `EnvironmentFile=/etc/publiclink/publiclink.env`) |
| `chat-app/` | Zalo chat bridge service — own schema `chat_schema`, own Liquibase changelog, port 9090 |
| `deploy/publiclink.env.example` | env template (secrets empty by design — fill before first boot) |
