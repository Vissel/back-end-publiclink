#!/bin/bash
#
# Deploy a specific version of share-link.top
#
# Usage:
#   ./deploy.sh <github_tag> <server_loc>
#
# Examples:
#   ./deploy.sh v1.2.3 /opt/tomcat
#   ./deploy.sh main /opt/tomcat
#   ./deploy.sh develop /opt/tomcat
#
# Workflow:
#   1. Clone backend + frontend repos at <github_tag> (or branch if main/develop)
#   2. Run build-and-deploy.sh to build and stage artifacts
#   3. Health checks: verify login page and public-key endpoint
#
set -euo pipefail

# ── Arguments ──────────────────────────────────────────────────────────────────
TAG="${1:?Usage: $0 <github_tag> <server_loc>}"
SERVER_LOC="${2:?Usage: $0 <github_tag> <server_loc>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Helper functions ───────────────────────────────────────────────────────────
log_step() {
  echo ""
  echo "══════════════════════════════════════════════════"
  echo "  ▶ $1"
  echo "══════════════════════════════════════════════════"
}

log_done() {
  echo "  ✓ $1"
}

log_error() {
  echo "  ✗ ERROR: $1" >&2
}

log_warn() {
  echo "  ⚠ $1"
}

# ── Step 0: Pre-flight checks ──────────────────────────────────────────────────
log_step "Checking prerequisites"

# Check git
if ! command -v git &> /dev/null; then
  log_error "git is not installed"
  exit 1
fi
log_done "git: $(git --version)"

# Check SSH key for GitHub
# Get the actual user's home directory (handle sudo correctly)
if [[ -n "${SUDO_USER:-}" ]]; then
  ACTUAL_HOME=$(eval echo ~$SUDO_USER)
else
  ACTUAL_HOME="$HOME"
fi

SSH_DIR="$ACTUAL_HOME/.ssh"
if [[ ! -d "$SSH_DIR" ]]; then
  log_error "SSH directory does not exist: $SSH_DIR"
  log_error "Run: ssh-keygen -t ed25519 -C \"your_email@example.com\""
  exit 1
fi

# Check for any private key (id_* or *.pem, excluding .pub files)
SSH_KEY_COUNT=$(find "$SSH_DIR" -maxdepth 1 -type f \( -name "id_*" -o -name "*.pem" \) ! -name "*.pub" 2>/dev/null | wc -l)
if [[ "$SSH_KEY_COUNT" -eq 0 ]]; then
  log_error "No SSH private keys found in $SSH_DIR"
  log_error "Run: ssh-keygen -t ed25519 -C \"your_email@example.com\""
  log_error "Then add the public key to GitHub: https://github.com/settings/keys"
  exit 1
fi
log_done "SSH key: found $SSH_KEY_COUNT private key(s) in $SSH_DIR"

# Optional: check if ssh-agent has keys loaded (non-fatal warning)
if command -v ssh-add &> /dev/null; then
  if ssh-add -l &> /dev/null; then
    LOADED_KEYS=$(ssh-add -l 2>/dev/null | wc -l)
    log_done "SSH agent: $LOADED_KEYS key(s) loaded"
  else
    log_warn "SSH agent: no keys loaded (run: ssh-add $SSH_DIR/id_ed25519)"
  fi
fi

# Check JDK 25
# Try to auto-detect JDK 25 if JAVA_HOME is not set or points to an older JDK
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  log_warn "JAVA_HOME not set or invalid — searching for JDK 25..."
  for jdk_path in /usr/lib/jvm/java-25-* /usr/lib/jvm/java-25 /opt/jdk-25* /usr/java/jdk-25*; do
    if [[ -x "$jdk_path/bin/java" ]]; then
      JAVA_HOME="$jdk_path"
      export JAVA_HOME
      export PATH="$JAVA_HOME/bin:$PATH"
      log_done "Auto-detected JDK 25: $JAVA_HOME"
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  log_error "JAVA_HOME is not set and JDK 25 could not be auto-detected"
  log_error "Set it manually: export JAVA_HOME=/path/to/jdk-25"
  log_error "Common locations: /usr/lib/jvm/java-25-openjdk-amd64, /opt/jdk-25*"
  exit 1
fi

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  log_error "JAVA_HOME/bin/java is not executable: $JAVA_HOME"
  exit 1
fi

JAVA_VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)"
if [[ "$JAVA_VERSION" -lt 25 ]]; then
  log_error "JDK ${JAVA_VERSION} found at $JAVA_HOME, but JDK 25+ is required"
  log_error "Install JDK 25 or update JAVA_HOME to point to a JDK 25 installation"
  log_error "Common locations: /usr/lib/jvm/java-25-openjdk-amd64, /opt/jdk-25*"
  exit 1
fi
log_done "JDK: ${JAVA_VERSION} (JAVA_HOME=$JAVA_HOME)"

# Check Maven
if ! command -v mvn &> /dev/null; then
  log_error "Maven is not installed"
  exit 1
fi
MVN_VERSION="$(mvn -version 2>&1 | head -1 | grep -oP 'Apache Maven \K[0-9]+\.[0-9]+' || echo "unknown")"
log_done "Maven: ${MVN_VERSION}"

# Check Node.js
if ! command -v node &> /dev/null; then
  log_error "Node.js is not installed"
  exit 1
fi
NODE_VERSION="$(node --version | cut -d'v' -f2 | cut -d'.' -f1)"
if [[ "$NODE_VERSION" -lt 18 ]]; then
  log_error "Node.js ${NODE_VERSION} found, but Node.js 18+ is required"
  exit 1
fi
log_done "Node.js: $(node --version)"

# Check npm
if ! command -v npm &> /dev/null; then
  log_error "npm is not installed"
  exit 1
fi
log_done "npm: $(npm --version)"

# ── Step 1: Clone repositories ─────────────────────────────────────────────────
log_step "Cloning repositories at ${TAG}"

cd "$SCRIPT_DIR"

# Determine if it's a branch or tag
if [[ "$TAG" == "main" || "$TAG" == "develop" ]]; then
  BRANCH="$TAG"
  log_warn "Cloning branch: $BRANCH"
else
  BRANCH="$TAG"
  log_done "Will clone tag: $TAG"
fi

# Clone backend
BACKEND_DIR="$SCRIPT_DIR/back-end-publiclink"
if [[ -d "$BACKEND_DIR" ]]; then
  log_warn "Removing existing backend directory"
  rm -rf "$BACKEND_DIR"
fi

git clone --branch "$BRANCH" --depth 1 git@github.com:Vissel/back-end-publiclink.git || {
  log_error "Failed to clone backend repository (tag/branch: $BRANCH)"
  exit 1
}
log_done "Backend cloned to $BACKEND_DIR"

# Clone frontend
FRONTEND_DIR="$SCRIPT_DIR/frontend-public-link"
if [[ -d "$FRONTEND_DIR" ]]; then
  log_warn "Removing existing frontend directory"
  rm -rf "$FRONTEND_DIR"
fi

git clone --branch "$BRANCH" --depth 1 git@github.com:Vissel/frontend-public-link.git || {
  log_error "Failed to clone frontend repository (tag/branch: $BRANCH)"
  exit 1
}
log_done "Frontend cloned to $FRONTEND_DIR"

# ── Step 2: Build and deploy ───────────────────────────────────────────────────
log_step "Building and deploying to ${SERVER_LOC}/webapps"

cd "$BACKEND_DIR"

# Check if build-and-deploy.sh exists
if [[ ! -f build-and-deploy.sh ]]; then
  log_error "build-and-deploy.sh not found in backend repository"
  exit 1
fi

# Make it executable
chmod +x build-and-deploy.sh

# Run the build and deploy script
./build-and-deploy.sh "${SERVER_LOC}/webapps" prod || {
  log_error "Build and deploy failed"
  exit 1
}
log_done "Build and deploy completed"

# ── Step 3: Health checks ──────────────────────────────────────────────────────
log_step "Running health checks"

# Wait a moment for services to start/reload
log_warn "Waiting 5 seconds for services to initialize..."
sleep 5

HEALTH_OK=true

# Check 1: Login page (expect HTTP 200)
log_step "Health check 1: Login page"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -I https://share-link.top/login 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "200" ]]; then
  log_done "Login page: HTTP $HTTP_CODE"
else
  log_error "Login page: HTTP $HTTP_CODE (expected 200)"
  HEALTH_OK=false
fi

# Check 2: Public key endpoint (expect non-empty, non-204)
log_step "Health check 2: Public key endpoint"
RESPONSE=$(curl -s https://share-link.top/apiGateway/api/v1/auth/public-key 2>/dev/null || echo "")
HTTP_CODE_KEY=$(curl -s -o /dev/null -w "%{http_code}" https://share-link.top/apiGateway/api/v1/auth/public-key 2>/dev/null || echo "000")

if [[ -z "$RESPONSE" ]]; then
  log_error "Public key endpoint: empty response"
  HEALTH_OK=false
elif [[ "$HTTP_CODE_KEY" == "204" ]]; then
  log_error "Public key endpoint: HTTP 204 (no content)"
  HEALTH_OK=false
elif [[ "$HTTP_CODE_KEY" == "200" ]]; then
  log_done "Public key endpoint: HTTP $HTTP_CODE_KEY, response length: ${#RESPONSE} bytes"
else
  log_warn "Public key endpoint: HTTP $HTTP_CODE_KEY (non-200, but non-empty)"
fi

# Check 3: Chat bridge (chat-app on :9090 through the gateway /chat/** route)
# A 200 here proves nginx, the gateway route and chat-app together, so a missing
# unit or a wrong route order cannot pass silently.
log_step "Health check 3: Chat bridge"
CHAT_BODY=$(curl -s --max-time 10 https://share-link.top/apiGateway/chat/api/v1/chat/health 2>/dev/null || echo "")
HTTP_CODE_CHAT=$(curl -s --max-time 10 -o /dev/null -w "%{http_code}" https://share-link.top/apiGateway/chat/api/v1/chat/health 2>/dev/null || echo "000")

if [[ "$HTTP_CODE_CHAT" == "200" && "$CHAT_BODY" == *'"status":"UP"'* ]]; then
  log_done "Chat bridge: HTTP $HTTP_CODE_CHAT — $CHAT_BODY"
elif [[ "$HTTP_CODE_CHAT" == "000" ]]; then
  log_error "Chat bridge: no response — is publiclink-chat running? (systemctl status publiclink-chat)"
  HEALTH_OK=false
else
  log_error "Chat bridge: HTTP $HTTP_CODE_CHAT, body: ${CHAT_BODY:0:160} (expected 200 with status UP)"
  HEALTH_OK=false
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
if [[ "$HEALTH_OK" == true ]]; then
  echo "  ✓ Deployment successful — all health checks passed"
else
  echo "  ✗ Deployment completed — but some health checks failed"
fi
echo "══════════════════════════════════════════════════"
echo ""
echo "Tag/Branch: ${TAG}"
echo "Server:     ${SERVER_LOC}"
echo "Backend:    ${BACKEND_DIR}"
echo "Frontend:   ${FRONTEND_DIR}"
echo ""

if [[ "$HEALTH_OK" == true ]]; then
  exit 0
else
  log_error "Review the logs above for details"
  exit 1
fi
