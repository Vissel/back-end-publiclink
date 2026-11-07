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

# Change the HTTP connector port from 8080 to 8081 and bind to localhost
# This is a destructive edit: the old 8080 connector is replaced.
sed -i 's/<Connector port="8080"/<Connector port="8081" address="127.0.0.1"/' "$SERVER_XML"
echo "  Updated connector: port 8081, address 127.0.0.1"

# Restart Tomcat to apply
systemctl restart tomcat 2>/dev/null || systemctl restart tomcat@* 2>/dev/null || {
  echo "  ⚠ Could not restart Tomcat via systemctl. Restart it manually."
}

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
    echo "  Installed $key → $RSA_KEYS_DEST/"
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
else
  echo "  $ENV_FILE_DEST already exists — skipping (manual edit required)"
fi

# ── Step 4: Install systemd units ─────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Installing systemd units to ${SYSTEMD_DEST}"
echo "══════════════════════════════════════════════════"

mkdir -p "$SYSTEMD_DEST"
for unit in publiclink-gateway.service publiclink-app.service publiclink-user.service; do
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

systemctl reload nginx
echo "  Reloaded nginx"

# ── Step 6: Enable and start services ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Enabling and starting services"
echo "══════════════════════════════════════════════════"

for svc in publiclink-gateway publiclink-app publiclink-user; do
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
echo ""
echo "Done."
