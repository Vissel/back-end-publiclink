#!/bin/bash
#
# One-time server provisioning for share-link.top
#
# This script configures the server for the first time:
#   - Tomcat: port 8081, localhost only (gateway owns 8080)
#   - RSA keys: copied to destination directory
#   - Systemd units: installed and enabled
#   - nginx: configured and enabled
#
# Usage:
#   sudo ./setup-server.sh <TOMCAT_HOME> <KEYS_SRC_DIR>
#
# Example:
#   sudo ./setup-server.sh /opt/tomcat apiGateway/src/main/resources/auth-service
#
# Environment variables (override defaults):
#   RSA_KEYS_DEST    — where to copy PEMs (default: /opt/publiclink/keys)
#   ENV_FILE_DEST    — runtime secrets file (default: /etc/publiclink/publiclink.env)
#   SYSTEMD_DEST     — systemd units directory (default: /etc/systemd/system)
#   NGINX_AVAILABLE  — nginx config source dir (auto-detected if not set)
#   NGINX_ENABLED    — nginx config enabled dir (auto-detected if not set)
#
# Prerequisites:
#   - Run as root (sudo)
#   - Tomcat installed at <TOMCAT_HOME>
#   - RSA PEM files (auth260222.pem, pubauth.pem) in <KEYS_SRC_DIR>
#   - nginx installed
#   - systemd available
#
set -euo pipefail

# ── Configurable destinations (override via environment) ──────────────────────
RSA_KEYS_DEST="${RSA_KEYS_DEST:-/opt/publiclink/keys}"
ENV_FILE_DEST="${ENV_FILE_DEST:-/etc/publiclink/publiclink.env}"
SYSTEMD_DEST="${SYSTEMD_DEST:-/etc/systemd/system}"

# ── Arguments ──────────────────────────────────────────────────────────────────
TOMCAT_HOME="${1:?Usage: $0 <TOMCAT_HOME> <KEYS_SRC_DIR>}"
KEYS_SRC_DIR="${2:?Usage: $0 <TOMCAT_HOME> <KEYS_SRC_DIR>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$SCRIPT_DIR/deploy"

if [[ ! -d "$TOMCAT_HOME" ]]; then
  echo "✗ Tomcat directory does not exist: ${TOMCAT_HOME}"
  exit 1
fi

if [[ ! -d "$KEYS_SRC_DIR" ]]; then
  echo "✗ Keys source directory does not exist: ${KEYS_SRC_DIR}"
  exit 1
fi

if [[ $EUID -ne 0 ]]; then
  echo "✗ This script must be run as root (sudo)"
  exit 1
fi

# ── Step 1: Configure Tomcat ──────────────────────────────────────────────────
echo "══════════════════════════════════════════════════"
echo "  Configuring Tomcat (port 8081, localhost only)"
echo "══════════════════════════════════════════════════"

SERVER_XML="$TOMCAT_HOME/conf/server.xml"
if [[ ! -f "$SERVER_XML" ]]; then
  echo "✗ Tomcat server.xml not found: ${SERVER_XML}"
  exit 1
fi

# Backup the original
cp -f "$SERVER_XML" "${SERVER_XML}.bak.$(date +%Y%m%d_%H%M%S)"
echo "  Backed up server.xml"

# Change the HTTP connector port from 8080 to 8081 and bind to localhost.
# This is a destructive edit: the old 8080 connector is replaced.
# Perl slurp mode (-0) matches the Connector element even when it spans
# several lines, and drops EVERY pre-existing address="..." attribute first
# so the XML can never end up with a duplicate (Tomcat then refuses to start
# with "Attribute address was already specified").
perl -0pi -e '
    s{ <Connector \s+ port="8080" (?=[\s>]) ([^>]*?) > }{
        my $attrs = $1;
        $attrs =~ s/\s+address\s*=\s*"[^"]*"//g;
        qq{<Connector port="8081" address="127.0.0.1"$attrs>};
    }xse' "$SERVER_XML"
echo "  Updated connector: port 8081, address 127.0.0.1"

# Restart Tomcat to apply
systemctl restart tomcat 2>/dev/null || systemctl restart tomcat@* 2>/dev/null || {
  echo "  ⚠ Could not restart Tomcat via systemctl. Restart it manually."
}

# Verify the connector actually changed and carries exactly one address
# attribute (perl exit codes: 0 = clean, 1 = missing or duplicate).
if perl -0ne 'my ($c) = /<Connector\b[^>]*port="8081"[^>]*>/; if (defined $c && (() = $c =~ /\baddress\s*=/g) == 1) { exit 0 } exit 1' "$SERVER_XML"; then
  echo "  ✓ Verified: connector 8081 with a single address attribute"
else
  echo "  ✗ ERROR: connector is NOT clean on 8081 — server.xml format was unexpected"
  echo "    Open $SERVER_XML and change the HTTP connector manually:"
  echo "    <Connector port=\"8081\" address=\"127.0.0.1\" ... />"
fi

# ── Step 1c: SPA fallback valve (RewriteValve) ───────────────────────────────
# Without it, /login and other deep routes answer 404 (the web.xml error page
# serves index.html but KEEPS the 404 status, failing health checks). The
# RewriteValve rewrites them to /index.html with a real 200 before the error
# page kicks in.
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing RewriteValve (SPA deep-routes → 200)"
echo "══════════════════════════════════════════════════"

if grep -q 'RewriteValve' "$SERVER_XML"; then
  echo "  ✓ RewriteValve already present in server.xml"
else
  sed -i '/<\/Host>/i\      <Valve className="org.apache.catalina.valves.rewrite.RewriteValve" />' "$SERVER_XML"
  echo "  Added RewriteValve to <Host>"
fi

REWRITE_CONF="$TOMCAT_HOME/conf/Catalina/localhost/rewrite.config"
mkdir -p "$(dirname "$REWRITE_CONF")"
cat > "$REWRITE_CONF" <<'EOF'
# SPA fallback: rewrite deep routes to /index.html with a 200 status.
# /static/ and real files pass through untouched.
RewriteCond %{REQUEST_URI} !^/static/
RewriteCond %{REQUEST_FILENAME} !-f
RewriteRule ^ /index.html [L]
EOF
echo "  Installed rewrite.config → $REWRITE_CONF"
systemctl restart tomcat 2>/dev/null || echo "  ⚠ Could not restart Tomcat via systemctl. Restart it manually."

# ── Step 1d: Install runtime dependencies (Redis + RabbitMQ) ──────────────────
# The gateway rate limiter executes a Lua script in Redis on EVERY request
# (rate-limit.enabled=true in ratelimiter.yml, fallback-strategy DENY). With
# Redis down the filter errors and GlobalExceptionHandler mislabels the failure
# as AUTH_001 "Invalid username or password". RabbitMQ receives gateway events.
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing Redis + RabbitMQ"
echo "══════════════════════════════════════════════════"

if command -v redis-server >/dev/null 2>&1; then
  echo "  ✓ redis-server already installed"
else
  apt-get update -qq
  apt-get install -y redis-server
  echo "  Installed redis-server"
fi
systemctl enable --now redis-server 2>/dev/null || echo "  ⚠ Could not start redis-server"

if command -v rabbitmq-server >/dev/null 2>&1; then
  echo "  ✓ rabbitmq-server already installed"
else
  apt-get install -y rabbitmq-server
  echo "  Installed rabbitmq-server"
fi
systemctl enable --now rabbitmq-server 2>/dev/null || echo "  ⚠ Could not start rabbitmq-server"

# ── Step 1b: Create the service account ───────────────────────────────────────
# The systemd units run as User=publiclink; systemd fails every start with
# status=217/USER if the account does not exist.
echo ""
echo "══════════════════════════════════════════════════"
echo "  Creating service account: publiclink"
echo "══════════════════════════════════════════════════"

if id -u publiclink >/dev/null 2>&1; then
  echo "  ✓ User publiclink already exists"
else
  useradd --system --no-create-home --shell /usr/sbin/nologin publiclink
  echo "  ✓ Created system user publiclink"
fi

# The services' WorkingDirectory must be owned by the account so the JVMs can
# write temp/log files there.
mkdir -p "$(dirname "$RSA_KEYS_DEST")"
chown publiclink:publiclink "$(dirname "$RSA_KEYS_DEST")"

# ── Step 2: Install RSA keys ──────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing RSA keys to ${RSA_KEYS_DEST}"
echo "══════════════════════════════════════════════════"

mkdir -p "$RSA_KEYS_DEST"
for key in auth260222.pem pubauth.pem; do
  if [[ -f "$KEYS_SRC_DIR/$key" ]]; then
    cp -f "$KEYS_SRC_DIR/$key" "$RSA_KEYS_DEST/"
    chmod 600 "$RSA_KEYS_DEST/$key"
    # The services run as User=publiclink and read these keys via
    # java.nio.file.Paths.get — root-only 600 would make login encryption fail.
    chown publiclink:publiclink "$RSA_KEYS_DEST/$key"
    echo "  Installed $key → $RSA_KEYS_DEST/ (owner: publiclink)"
  else
    echo "  ⚠ Missing: $KEYS_SRC_DIR/$key"
  fi
done

# ── Step 3: Install environment file ──────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing ${ENV_FILE_DEST}"
echo "══════════════════════════════════════════════════"

mkdir -p "$(dirname "$ENV_FILE_DEST")"
if [[ ! -f "$ENV_FILE_DEST" ]]; then
  cp -f "$DEPLOY_DIR/publiclink.env.example" "$ENV_FILE_DEST"
  chmod 600 "$ENV_FILE_DEST"
  echo "  Installed publiclink.env from template → $ENV_FILE_DEST"
  echo "  ⚠ You must edit $ENV_FILE_DEST and fill in:"
  echo "      JWT_KEY, JWT_URLKEY, DB_*, USER_DB_*, MAIL_*"
  echo "      and, for the Zalo chat bridge: CHAT_DB_*, ZALO_* (leave"
  echo "      ZALO_ENABLED=false until the OA + Zalo App + OAuth consent exist)"
  echo "      optional, for out-of-tab chat notifications: CHAT_VAPID_PUBLIC_KEY and"
  echo "      CHAT_VAPID_PRIVATE_KEY (generate with: npx --yes web-push generate-vapid-keys)"
else
  echo "  $ENV_FILE_DEST already exists — skipping (manual edit required)"
fi

# ── Step 4: Install systemd units ─────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing systemd units to ${SYSTEMD_DEST}"
echo "══════════════════════════════════════════════════"

mkdir -p "$SYSTEMD_DEST"
for unit in publiclink-gateway.service publiclink-app.service publiclink-user.service publiclink-chat.service; do
  if [[ -f "$DEPLOY_DIR/systemd/$unit" ]]; then
    cp -f "$DEPLOY_DIR/systemd/$unit" "$SYSTEMD_DEST/"
    echo "  Installed $unit → $SYSTEMD_DEST/"
  else
    echo "  ⚠ Missing: $DEPLOY_DIR/systemd/$unit"
  fi
done

systemctl daemon-reload
echo "  Reloaded systemd daemon"

# ── Step 5: Install nginx config ──────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing nginx config"
echo "══════════════════════════════════════════════════"

NGINX_CONF="$DEPLOY_DIR/nginx/share-link.top.conf"
if [[ ! -f "$NGINX_CONF" ]]; then
  echo "✗ nginx config not found: ${NGINX_CONF}"
  exit 1
fi

# Detect nginx config directory (override via environment)
if [[ -z "${NGINX_AVAILABLE:-}" ]]; then
  if [[ -d /etc/nginx/sites-available ]]; then
    NGINX_AVAILABLE="/etc/nginx/sites-available"
  elif [[ -d /etc/nginx/conf.d ]]; then
    NGINX_AVAILABLE="/etc/nginx/conf.d"
  else
    echo "✗ Could not detect nginx config directory. Set NGINX_AVAILABLE and NGINX_ENABLED."
    exit 1
  fi
fi
if [[ -z "${NGINX_ENABLED:-}" ]]; then
  NGINX_ENABLED="$NGINX_AVAILABLE"
fi

cp -f "$NGINX_CONF" "$NGINX_AVAILABLE/share-link.top.conf"
if [[ "$NGINX_AVAILABLE" != "$NGINX_ENABLED" ]]; then
  ln -sf "$NGINX_AVAILABLE/share-link.top.conf" "$NGINX_ENABLED/share-link.top.conf"
fi
echo "  Installed share-link.top.conf → $NGINX_AVAILABLE/"

# Test nginx config
nginx -t || {
  echo "✗ nginx config test failed"
  exit 1
}

# Reload nginx if running; otherwise start it (reload on a stopped service fails)
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
  echo "  Reloaded nginx"
else
  systemctl start nginx
  echo "  nginx was not running — started it"
fi

# ── Step 6: Enable and start services ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Enabling and starting services"
echo "══════════════════════════════════════════════════"

# Make sure nginx survives a reboot too
systemctl enable nginx 2>/dev/null || echo "  ⚠ Could not enable nginx"

for svc in publiclink-gateway publiclink-app publiclink-user publiclink-chat; do
  systemctl enable "$svc" 2>/dev/null || echo "  ⚠ Could not enable $svc"
  systemctl start "$svc" 2>/dev/null || echo "  ⚠ Could not start $svc (check journalctl -u $svc)"
  echo "  Enabled and started $svc"
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Server setup complete"
echo "══════════════════════════════════════════════════"
echo ""
echo "Next steps:"
echo "  1. Edit $ENV_FILE_DEST and fill in secrets"
echo "  2. Restart services: systemctl restart publiclink-*"
echo "  3. Verify: curl -I https://share-link.top/login"
echo "  4. Verify: curl -s https://share-link.top/apiGateway/api/v1/auth/public-key"
echo "  5. Verify chat bridge: curl -s https://share-link.top/apiGateway/chat/api/v1/chat/health"
echo ""
echo "Done."
