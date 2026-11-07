#!/bin/bash
#
# Build backend (4 modules) + frontend, then stage them for deployment.
#
# Target topology (see deploy/nginx/share-link.top.conf):
#   nginx :443
#     /apiGateway/  -> apiGateway  127.0.0.1:8080  (standalone: java -jar)
#     /             -> Tomcat      127.0.0.1:8081  (webapps/ROOT = React build)
#
# Usage:
#   ./build-and-deploy.sh <TOMCAT_WEBAPPS_DIR> [maven-profile]
#
# Examples:
#   ./build-and-deploy.sh /opt/tomcat/webapps
#   ./build-and-deploy.sh /opt/tomcat/webapps dev
#
set -euo pipefail

# ── Arguments ──────────────────────────────────────────────────────────────────
TOMCAT_DIR="${1:?Usage: $0 <TOMCAT_WEBAPPS_DIR> [maven-profile]}"
PROFILE="${2:-prod}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# frontend-public-link and back-end-publiclink are sibling directories.
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../frontend-public-link" && pwd)"
# Runnable service artifacts are collected here, ready to ship to the server.
ARTIFACTS_DIR="$SCRIPT_DIR/target/deploy"

if [[ ! -d "$TOMCAT_DIR" ]]; then
  echo "✗ Target directory does not exist: ${TOMCAT_DIR}"
  exit 1
fi

# ── Resolve Maven profile ─────────────────────────────────────────────────────
echo "▶ Using Maven profile: ${PROFILE}"

# ── Step 0: Pre-flight ────────────────────────────────────────────────────────
# The POMs target Java 25 (root pom <java.version>). A JDK 21 or 17 fails with
# the opaque "invalid target release: 25", so check up front. Try to auto-detect
# JDK 25 if JAVA_HOME is not set or points to an older JDK.
REQUIRED_JAVA="$(sed -n 's:.*<java.version>\(.*\)</java.version>.*:\1:p' "$SCRIPT_DIR/pom.xml" | head -1)"

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "⚠ JAVA_HOME not set or invalid — searching for JDK ${REQUIRED_JAVA}..."
  for jdk_path in /usr/lib/jvm/java-${REQUIRED_JAVA}-* /usr/lib/jvm/java-${REQUIRED_JAVA} /opt/jdk-${REQUIRED_JAVA}* /usr/java/jdk-${REQUIRED_JAVA}*; do
    if [[ -x "$jdk_path/bin/java" ]]; then
      JAVA_HOME="$jdk_path"
      export JAVA_HOME
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "  ✓ Auto-detected JDK ${REQUIRED_JAVA}: $JAVA_HOME"
      break
    fi
  done
fi

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  echo "✗ JAVA_HOME is not set and JDK ${REQUIRED_JAVA} could not be auto-detected"
  echo "  Fix: export JAVA_HOME=/path/to/jdk-${REQUIRED_JAVA}"
  echo "       export PATH=\"\$JAVA_HOME/bin:\$PATH\""
  echo "  Common locations: /usr/lib/jvm/java-${REQUIRED_JAVA}-openjdk-amd64, /opt/jdk-${REQUIRED_JAVA}*"
  exit 1
fi

JAVA_MAJOR="$("$JAVA_BIN" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [[ -z "$JAVA_MAJOR" ]]; then
  echo "✗ Could not read a Java version from: ${JAVA_BIN} -version"
  exit 1
fi
if [[ -n "$REQUIRED_JAVA" && "$JAVA_MAJOR" -lt "$REQUIRED_JAVA" ]]; then
  echo "✗ JDK ${JAVA_MAJOR} found at ${JAVA_BIN}, but this project targets Java ${REQUIRED_JAVA}."
  echo "  Fix: export JAVA_HOME=/path/to/jdk-${REQUIRED_JAVA}"
  echo "       export PATH=\"\$JAVA_HOME/bin:\$PATH\""
  echo "  Common locations: /usr/lib/jvm/java-${REQUIRED_JAVA}-openjdk-amd64, /opt/jdk-${REQUIRED_JAVA}*"
  exit 1
fi
echo "▶ Building with JDK ${JAVA_MAJOR} (${JAVA_BIN}; project target: ${REQUIRED_JAVA})"

# ── Step 1: Build backend (4 modules) ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Building backend: adapter, apiGateway,"
echo "  publiclink-app, user"
echo "══════════════════════════════════════════════════"
echo ""

cd "$SCRIPT_DIR"
mvn clean package -DskipTests -P"$PROFILE"

echo ""
echo "▶ Backend build completed."

# ── Step 2: Build frontend ────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Building frontend"
echo "══════════════════════════════════════════════════"
echo ""

cd "$FRONTEND_DIR"
# react-scripts is often missing after a partial install; npm run build then
# fails with "'react-scripts' is not recognized".
if [[ ! -d node_modules/react-scripts ]]; then
  echo "  node_modules incomplete — installing dependencies"
  # npm ci is reproducible but hard-fails when package-lock.json has drifted
  # from package.json; fall back to npm install rather than blocking the deploy.
  npm ci || npm install
fi
npm run build

# Copy SPA fallback web.xml for BrowserRouter
echo "  Copying tomcat-config/WEB-INF/web.xml → build/WEB-INF/"
mkdir -p "$FRONTEND_DIR/build/WEB-INF"
cp -f "$FRONTEND_DIR/tomcat-config/WEB-INF/web.xml" "$FRONTEND_DIR/build/WEB-INF/web.xml"

echo ""
echo "▶ Frontend build completed."

# ── Step 3: Deploy frontend to the ROOT context ───────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Deploying frontend to: ${TOMCAT_DIR}/ROOT"
echo "══════════════════════════════════════════════════"
echo ""

# 3a. The frontend must live at webapps/ROOT so that https://share-link.top/
#     serves index.html directly. Deploying it under /publiclink would make the
#     login page live at https://share-link.top/publiclink/ instead.
FRONTEND_DEST="${TOMCAT_DIR}/ROOT"
if [[ -d "$FRONTEND_DEST" ]]; then
  echo "  Removing old frontend: ${FRONTEND_DEST}"
  rm -rf "$FRONTEND_DEST"
fi
# A leftover ROOT.war would be re-expanded by Tomcat and overwrite the build.
if [[ -f "${TOMCAT_DIR}/ROOT.war" ]]; then
  echo "  Removing stock Tomcat ROOT.war: ${TOMCAT_DIR}/ROOT.war"
  rm -f "${TOMCAT_DIR}/ROOT.war"
fi
echo "  Deploying frontend → ${FRONTEND_DEST}"
cp -r "$FRONTEND_DIR/build" "$FRONTEND_DEST"

# ── Step 4: Stage the standalone services ─────────────────────────────────────
# apiGateway, publiclink-app and user all run as executable Spring Boot
# archives on a JVM. None of them can be dropped into webapps/:
# Spring Cloud Gateway is not supported in a servlet container, and the other
# two are plain WebFlux apps. Nginx fronts them (see deploy/nginx/).
rm -rf "$ARTIFACTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

for artifact in \
  "apiGateway/target/apiGateway.war" \
  "publiclink-app/target/publiclink-exec.jar" \
  "user/target/user-0.0.1-SNAPSHOT.jar"; do
  src="$SCRIPT_DIR/$artifact"
  if [[ -f "$src" ]]; then
    cp -f "$src" "$ARTIFACTS_DIR/"
    echo "  Staged $(basename "$src")"
  else
    echo "  ⚠ Missing: ${artifact}" >&2
  fi
done

# ── Step 5: Summary ───────────────────────────────────────────────────────────
echo ""
echo "▶ Frontend  → ${FRONTEND_DEST}"
echo "▶ Services  → ${ARTIFACTS_DIR}"
echo ""
echo "── Start each service (systemd templates in deploy/systemd/) ──"
echo "  java -jar apiGateway.war                  # :8080"
echo "  java -jar publiclink-exec.jar             # :9080"
echo "  java -jar user-0.0.1-SNAPSHOT.jar         # :8082"
echo ""
echo "Remember: copy the RSA PEMs to /opt/publiclink/keys/ and restart Tomcat."
echo ""
echo "Done."
