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
#   2. Run the adjacent build-and-deploy.sh to build and install release files
#   3. Check currently running endpoints (service restarts remain manual)
#
# Run as a sudo-capable login user, without sudo. Only installation elevates.
# DEPLOY_WORK_DIR overrides the private checkout workspace under ~/.cache.
# Backend destination/account overrides are forwarded to build-and-deploy.sh.
#
set -euo pipefail

# ── Arguments ──────────────────────────────────────────────────────────────────
TAG="${1:?Usage: $0 <github_tag> <server_loc>}"
SERVER_LOC="${2:?Usage: $0 <github_tag> <server_loc>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_LOC="${SERVER_LOC%/}"
if [[ "$EUID" -eq 0 || "$(id -un)" == "${TOMCAT_USER:-tomcat}" ||
      "$(id -un)" == "${PUBLICLINK_USER:-publiclink}" ]]; then
  echo "✗ Run this script as your sudo-capable login user, without sudo." >&2
  exit 1
fi
if [[ "$SERVER_LOC" != /* || "$SERVER_LOC" == / ]]; then
  echo "✗ Server location must be an absolute Tomcat home directory, not /." >&2
  exit 1
fi
umask 077

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

# Git uses this login user's SSH configuration/agent, including custom key names.
# Authentication is checked by clone, not by guessing private-key filenames.
SSH_DIR="$HOME/.ssh"
if [[ ! -f "$SCRIPT_DIR/build-and-deploy.sh" ]]; then
  log_error "Keep the updated build-and-deploy.sh beside this deploy.sh"
  exit 1
fi
if ! command -v sudo >/dev/null || ! sudo -v; then
  log_error "Administrator sudo access is required for release installation"
  exit 1
fi

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

# Use a fresh private checkout for each run. Never delete/reuse old checkouts:
# they may contain local changes or root-owned files from a previous sudo run.
WORK_ROOT="${DEPLOY_WORK_DIR:-$HOME/.cache/publiclink-deploy}"
mkdir -p "$WORK_ROOT"
WORK_ROOT="$(cd "$WORK_ROOT" && pwd)"
if [[ ! -O "$WORK_ROOT" || ! -w "$WORK_ROOT" ]]; then
  log_error "Workspace must be owned and writable by $(id -un): $WORK_ROOT"
  log_error "Set DEPLOY_WORK_DIR to a new directory owned by your login user"
  exit 1
fi
WORK_DIR="$(mktemp -d "$WORK_ROOT/release.XXXXXX")"
log_done "Private workspace: $WORK_DIR (retained for diagnostics)"

# Determine if it's a branch or tag
if [[ "$TAG" == "main" || "$TAG" == "develop" ]]; then
  BRANCH="$TAG"
  log_warn "Cloning branch: $BRANCH"
else
  BRANCH="$TAG"
  log_done "Will clone tag: $TAG"
fi

# Clone backend
BACKEND_DIR="$WORK_DIR/back-end-publiclink"
git clone --branch "$BRANCH" --depth 1 git@github.com:Vissel/back-end-publiclink.git "$BACKEND_DIR" || {
  log_error "Failed to clone backend repository (tag/branch: $BRANCH)"
  exit 1
}
log_done "Backend cloned to $BACKEND_DIR"

# Clone frontend
FRONTEND_DIR="$WORK_DIR/frontend-public-link"
git clone --branch "$BRANCH" --depth 1 git@github.com:Vissel/frontend-public-link.git "$FRONTEND_DIR" || {
  log_error "Failed to clone frontend repository (tag/branch: $BRANCH)"
  exit 1
}
log_done "Frontend cloned to $FRONTEND_DIR"

# ── Step 2: Build and deploy ───────────────────────────────────────────────────
log_step "Installing frontend to ${SERVER_LOC}/webapps and backend to ${PUBLICLINK_DIR:-/opt/publiclink}"

# Always use the updated adjacent installer, even when deploying an older tag.
# Source code comes from the tag; deployment safety must not regress with it.
BACKEND_DIR="$BACKEND_DIR" FRONTEND_DIR="$FRONTEND_DIR" \
  bash "$SCRIPT_DIR/build-and-deploy.sh" "${SERVER_LOC}/webapps" prod || {
  log_error "Build and deploy failed"
  exit 1
}
log_done "Build and deploy completed"

# ── Step 3: Health checks ──────────────────────────────────────────────────────
log_step "Running health checks"

log_warn "Services were not restarted. These checks describe currently running endpoints."
log_warn "Restart services manually and recheck health to verify the installed release."

HEALTH_OK=true

# Check 1: Login page (expect HTTP 200)
log_step "Health check 1: Login page"
HTTP_CODE=$(curl -s --connect-timeout 5 --max-time 20 -o /dev/null -w "%{http_code}" -I https://share-link.top/login) || HTTP_CODE="000"
if [[ "$HTTP_CODE" == "200" ]]; then
  log_done "Login page: HTTP $HTTP_CODE"
else
  log_error "Login page: HTTP $HTTP_CODE (expected 200)"
  HEALTH_OK=false
fi

# Check 2: Public key endpoint (expect non-empty, non-204)
log_step "Health check 2: Public key endpoint"
KEY_RESULT=$(curl -s --connect-timeout 5 --max-time 20 -w $'\n%{http_code}' https://share-link.top/apiGateway/api/v1/auth/public-key) || KEY_RESULT=$'\n000'
HTTP_CODE_KEY="${KEY_RESULT##*$'\n'}"
RESPONSE="${KEY_RESULT%$'\n'*}"

if [[ -z "$RESPONSE" ]]; then
  log_error "Public key endpoint: empty response"
  HEALTH_OK=false
elif [[ "$HTTP_CODE_KEY" == "204" ]]; then
  log_error "Public key endpoint: HTTP 204 (no content)"
  HEALTH_OK=false
elif [[ "$HTTP_CODE_KEY" == "200" ]]; then
  log_done "Public key endpoint: HTTP $HTTP_CODE_KEY, response length: ${#RESPONSE} bytes"
else
  log_error "Public key endpoint: HTTP $HTTP_CODE_KEY (expected 200)"
  HEALTH_OK=false
fi

# Check 3: Chat bridge (chat-app on :9090 through the gateway /chat/** route)
# A 200 here proves nginx, the gateway route and chat-app together, so a missing
# unit or a wrong route order cannot pass silently.
log_step "Health check 3: Chat bridge"
CHAT_RESULT=$(curl -s --connect-timeout 5 --max-time 10 -w $'\n%{http_code}' https://share-link.top/apiGateway/chat/api/v1/chat/health) || CHAT_RESULT=$'\n000'
HTTP_CODE_CHAT="${CHAT_RESULT##*$'\n'}"
CHAT_BODY="${CHAT_RESULT%$'\n'*}"

if [[ "$HTTP_CODE_CHAT" == "200" && "$CHAT_BODY" == *'"status":"UP"'* ]]; then
  log_done "Chat bridge: HTTP $HTTP_CODE_CHAT — $CHAT_BODY"
elif [[ "$HTTP_CODE_CHAT" == "000" ]]; then
  log_error "Chat bridge: no response — is publiclink-chat running? (systemctl status publiclink-chat)"
  HEALTH_OK=false
else
  log_error "Chat bridge: HTTP $HTTP_CODE_CHAT, body: ${CHAT_BODY:0:160} (expected 200 with status UP)"
  HEALTH_OK=false
fi

# Check 4: Comment service (comment-app on :9091 through the gateway /comment/** route)
# The health path is under the permit-all /comment/api/v1/public/** namespace, so a
# 200 proves nginx, the gateway route and comment-app together without needing a token.
log_step "Health check 4: Comment service"
COMMENT_RESULT=$(curl -s --connect-timeout 5 --max-time 10 -w $'\n%{http_code}' https://share-link.top/apiGateway/comment/api/v1/public/health) || COMMENT_RESULT=$'\n000'
HTTP_CODE_COMMENT="${COMMENT_RESULT##*$'\n'}"
COMMENT_BODY="${COMMENT_RESULT%$'\n'*}"

if [[ "$HTTP_CODE_COMMENT" == "200" && "$COMMENT_BODY" == *'"status":"UP"'* ]]; then
  log_done "Comment service: HTTP $HTTP_CODE_COMMENT — $COMMENT_BODY"
elif [[ "$HTTP_CODE_COMMENT" == "000" ]]; then
  log_error "Comment service: no response — is publiclink-comment running? (systemctl status publiclink-comment)"
  HEALTH_OK=false
else
  log_error "Comment service: HTTP $HTTP_CODE_COMMENT, body: ${COMMENT_BODY:0:160} (expected 200 with status UP)"
  HEALTH_OK=false
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
if [[ "$HEALTH_OK" == true ]]; then
  echo "  ✓ Release files installed — currently running endpoints passed health checks"
else
  echo "  ✗ Release files installed — some currently running endpoints failed health checks"
fi
echo "══════════════════════════════════════════════════"
echo ""
echo "Tag/Branch: ${TAG}"
echo "Server:     ${SERVER_LOC}"
echo "Backend:    ${BACKEND_DIR}"
echo "Frontend:   ${FRONTEND_DIR}"
echo "Activation: manual service restart required; the new backend is not yet verified"
echo ""

if [[ "$HEALTH_OK" == true ]]; then
  exit 0
else
  log_error "Review the logs above for details"
  exit 1
fi
