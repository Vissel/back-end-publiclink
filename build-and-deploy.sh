#!/bin/bash
#
# Build backend (4 modules) + frontend, and deploy to Tomcat webapps.
#
# Usage:
#   ./build-and-deploy.sh <TOMCAT_WEBAPPS_DIR> [maven-profile]
#
# Examples:
#   ./build-and-deploy.sh /opt/tomcat/webapps
#   ./build-and-deploy.sh /opt/tomcat/webapps qa
#
set -euo pipefail

# ── Arguments ──────────────────────────────────────────────────────────────────
TOMCAT_DIR="${1:?Usage: $0 <TOMCAT_WEBAPPS_DIR> [maven-profile]}"
PROFILE="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../frontend/frontend-publiclink" && pwd)"

if [[ ! -d "$TOMCAT_DIR" ]]; then
  echo "✗ Target directory does not exist: ${TOMCAT_DIR}"
  exit 1
fi

# ── Resolve Maven profile flag ────────────────────────────────────────────────
PROFILE_FLAG=""
if [[ -n "$PROFILE" ]]; then
  PROFILE_FLAG="-P${PROFILE}"
  echo "▶ Using Maven profile: ${PROFILE}"
fi

# ── Step 1: Build backend (4 modules) ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Building backend: adapter, apiGateway,"
echo "  publiclink-app, user"
echo "══════════════════════════════════════════════════"
echo ""

cd "$SCRIPT_DIR"
mvn clean package -DskipTests $PROFILE_FLAG

echo ""
echo "▶ Backend build completed."

# ── Step 2: Build frontend ────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Building frontend"
echo "══════════════════════════════════════════════════"
echo ""

cd "$FRONTEND_DIR"
npm run build

# Copy SPA fallback web.xml for BrowserRouter
echo "  Copying tomcat-config/WEB-INF/web.xml → build/WEB-INF/"
mkdir -p "$FRONTEND_DIR/build/WEB-INF"
cp -f "$FRONTEND_DIR/tomcat-config/WEB-INF/web.xml" "$FRONTEND_DIR/build/WEB-INF/web.xml"

echo ""
echo "▶ Frontend build completed."

# ── Step 3: Deploy to Tomcat webapps ──────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Deploying to: ${TOMCAT_DIR}"
echo "══════════════════════════════════════════════════"
echo ""

deployed=0

# 3a. Deploy frontend as exploded directory → webapps/publiclink/
FRONTEND_DEST="${TOMCAT_DIR}/publiclink"
if [[ -d "$FRONTEND_DEST" ]]; then
  echo "  Removing old frontend: ${FRONTEND_DEST}"
  rm -rf "$FRONTEND_DEST"
fi
echo "  Deploying frontend → ${FRONTEND_DEST}"
cp -r "$FRONTEND_DIR/build" "$FRONTEND_DEST"
deployed=$((deployed + 1))

# 3b. Deploy backend WAR(s) → webapps/
for war_file in "$SCRIPT_DIR"/apiGateway/target/apiGateway-*.war; do
  if [[ -f "$war_file" ]]; then
    war_name="$(basename "$war_file")"
    app_name="${war_name%%-*}"          # e.g. apiGateway
    dest="${TOMCAT_DIR}/${app_name}.war"

    # Remove old exploded directory if present
    if [[ -d "${TOMCAT_DIR}/${app_name}" ]]; then
      echo "  Removing old exploded dir: ${TOMCAT_DIR}/${app_name}"
      rm -rf "${TOMCAT_DIR}/${app_name}"
    fi

    echo "  Deploying ${war_name} → ${dest}"
    cp -f "$war_file" "$dest"
    deployed=$((deployed + 1))
  fi
done

# ── Step 4: Summary ───────────────────────────────────────────────────────────
echo ""
echo "▶ Deployed ${deployed} artifact(s) to ${TOMCAT_DIR}"

echo ""
echo "── Standalone JARs (run separately, not in webapps) ──"
for jar_pattern in \
  "publiclink-app/target/publiclink-exec.jar" \
  "user/target/user-0.0.1-SNAPSHOT.jar"; do
  jar_file="$SCRIPT_DIR/$jar_pattern"
  if [[ -f "$jar_file" ]]; then
    echo "  $(basename "$jar_file")  →  java -jar $jar_file"
  fi
done

echo ""
echo "Done."
