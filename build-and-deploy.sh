#!/bin/bash
#
# Build backend (5 modules) + frontend, then install release files only.
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
# Run as a sudo-capable login user, never as root or a service account.
# Builds use the login user's environment; only installation uses sudo.
# nologin accounts need no password or login shell for file ownership.
# Overrides: TOMCAT_USER/TOMCAT_GROUP, PUBLICLINK_USER/PUBLICLINK_GROUP,
# PUBLICLINK_DIR (default /opt/publiclink), BACKEND_DIR, FRONTEND_DIR.
# Only webapps/ROOT, ROOT.war, and the four named backend archives are managed.
# Keep persistent frontend data/configuration outside ROOT; its old tree is backed up.
# Backend keys/configuration/uploads and external Tomcat/systemd config are untouched.
# Previous release files are retained outside webapps for recovery.
# Services are NOT restarted automatically.
#
set -euo pipefail

# ── Arguments ──────────────────────────────────────────────────────────────────
TOMCAT_DIR="${1:?Usage: $0 <TOMCAT_WEBAPPS_DIR> [maven-profile]}"
PROFILE="${2:-prod}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOMCAT_USER="${TOMCAT_USER:-tomcat}"
PUBLICLINK_USER="${PUBLICLINK_USER:-publiclink}"
PUBLICLINK_DIR="${PUBLICLINK_DIR:-/opt/publiclink}"

if [[ "$EUID" -eq 0 || "$(id -un)" == "$TOMCAT_USER" || "$(id -un)" == "$PUBLICLINK_USER" ]]; then
  echo "✗ Run this script as your sudo-capable login user, without sudo." >&2
  exit 1
fi
umask 077

# The wrapper can build a selected tag with this installed deployment script.
BACKEND_DIR="$(cd "${BACKEND_DIR:-$SCRIPT_DIR}" && pwd)"
FRONTEND_DIR="$(cd "${FRONTEND_DIR:-$BACKEND_DIR/../frontend-public-link}" && pwd)"
ARTIFACTS_DIR="$BACKEND_DIR/target/deploy"
ARTIFACTS=(
  "apiGateway/target/apiGateway.war"
  "publiclink-app/target/publiclink-exec.jar"
  "user/target/user-0.0.1-SNAPSHOT.jar"
  "chat-app/target/chat-exec.jar"
)

for tool in sudo flock runuser realpath install getent stat mvn npm; do
  command -v "$tool" >/dev/null || { echo "✗ Missing prerequisite: $tool" >&2; exit 1; }
done
TOMCAT_GROUP="${TOMCAT_GROUP:-$(id -gn "$TOMCAT_USER")}"
PUBLICLINK_GROUP="${PUBLICLINK_GROUP:-$(id -gn "$PUBLICLINK_USER")}"
id "$TOMCAT_USER" >/dev/null
id "$PUBLICLINK_USER" >/dev/null
getent group "$TOMCAT_GROUP" >/dev/null
getent group "$PUBLICLINK_GROUP" >/dev/null

# sudo authenticates the invoking login user, not tomcat/publiclink.
sudo -v || { echo "✗ Administrator sudo access is required for installation." >&2; exit 1; }
if [[ "$TOMCAT_DIR" != /* || "$PUBLICLINK_DIR" != /* ]]; then
  echo "✗ Deployment destinations must be absolute paths." >&2
  exit 1
fi
TOMCAT_DIR="$(sudo -n realpath -e -- "$TOMCAT_DIR")"
PUBLICLINK_DIR="$(sudo -n realpath -e -- "$PUBLICLINK_DIR")"
if [[ "$TOMCAT_DIR" == / || "$PUBLICLINK_DIR" == / ||
      "$TOMCAT_DIR" == "$PUBLICLINK_DIR" || "$TOMCAT_DIR" == "$PUBLICLINK_DIR/"* ||
      "$PUBLICLINK_DIR" == "$TOMCAT_DIR/"* ]]; then
  echo "✗ Deployment destinations must be separate, non-root directories." >&2
  exit 1
fi
for target in "$TOMCAT_DIR" "$PUBLICLINK_DIR"; do
  sudo -n test -d "$target" || { echo "✗ Not a directory: $target" >&2; exit 1; }
done
for access in -r -x; do
  sudo -n runuser -u "$TOMCAT_USER" -- test "$access" "$TOMCAT_DIR" || {
    echo "✗ $TOMCAT_USER cannot access $TOMCAT_DIR; check ancestor permissions/ACLs." >&2
    exit 1
  }
done
for access in -r -w -x; do
  sudo -n runuser -u "$PUBLICLINK_USER" -- test "$access" "$PUBLICLINK_DIR" || {
    echo "✗ $PUBLICLINK_USER cannot access $PUBLICLINK_DIR; check ancestor permissions/ACLs." >&2
    exit 1
  }
done

# ── Resolve Maven profile ─────────────────────────────────────────────────────
echo "▶ Using Maven profile: ${PROFILE}"

# ── Step 0: Pre-flight ────────────────────────────────────────────────────────
# The POMs target Java 25 (root pom <java.version>). A JDK 21 or 17 fails with
# the opaque "invalid target release: 25", so check up front. Try to auto-detect
# JDK 25 if JAVA_HOME is not set or points to an older JDK.
REQUIRED_JAVA="$(sed -n 's:.*<java.version>\(.*\)</java.version>.*:\1:p' "$BACKEND_DIR/pom.xml" | head -1)"

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
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "▶ Building with JDK ${JAVA_MAJOR} (${JAVA_BIN}; project target: ${REQUIRED_JAVA})"

# ── Step 1: Build backend (4 modules) ─────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "  Building backend: adapter, apiGateway,"
echo "  publiclink-app, user, chat-app"
echo "══════════════════════════════════════════════════"
echo ""

cd "$BACKEND_DIR"
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

# ── Step 3: Validate and stage every artifact before touching live files ──────
for artifact in "${ARTIFACTS[@]}"; do
  src="$BACKEND_DIR/$artifact"
  if [[ ! -s "$src" || ! -f "$src" || -L "$src" ]]; then
    echo "✗ Missing, empty, or linked artifact: $src; live files are unchanged." >&2
    exit 1
  fi
done
for file in index.html WEB-INF/web.xml; do
  if [[ ! -s "$FRONTEND_DIR/build/$file" ]]; then
    echo "✗ Missing frontend build/$file; live files are unchanged." >&2
    exit 1
  fi
done
mkdir -p "$ARTIFACTS_DIR"
for artifact in "${ARTIFACTS[@]}"; do
  cp -f "$BACKEND_DIR/$artifact" "$ARTIFACTS_DIR/"
done

# ── Step 4: Privileged installation only; no build tools run as root ───────────
# Builds may outlast sudo's timestamp. Refresh it BEFORE changing any live file.
sudo -v
sudo -n bash -s -- "$TOMCAT_DIR" "$PUBLICLINK_DIR" "$FRONTEND_DIR/build" \
  "$ARTIFACTS_DIR" "$TOMCAT_USER" "$TOMCAT_GROUP" "$PUBLICLINK_USER" "$PUBLICLINK_GROUP" <<'INSTALL_RELEASE'
set -euo pipefail
umask 077
tomcat_dir="$1"; publiclink_dir="$2"; build_dir="$3"; artifacts_dir="$4"
tomcat_user="$5"; tomcat_group="$6"; publiclink_user="$7"; publiclink_group="$8"
names=(apiGateway.war publiclink-exec.jar user-0.0.1-SNAPSHOT.jar chat-exec.jar)

# Directory locks serialize installers without adding a lock file to webapps.
exec 8<"$tomcat_dir"
exec 9<"$publiclink_dir"
flock -n 8 && flock -n 9 || { echo "✗ Another installation is running." >&2; exit 1; }

# Refuse ambiguous layouts instead of following links or replacing directories.
destinations=("$tomcat_dir/ROOT" "$tomcat_dir/ROOT.war")
for name in "${names[@]}"; do
  destinations+=("$publiclink_dir/$name")
done
for dest in "${destinations[@]}"; do
  [[ ! -L "$dest" ]] || { echo "✗ Symlink destination is not supported: $dest" >&2; exit 1; }
  if [[ -e "$dest" ]]; then
    if [[ "$dest" == "$tomcat_dir/ROOT" ]]; then
      test -d "$dest"
    else
      test -f "$dest"
    fi
  fi
done
linked_file="$(find "$build_dir" -type l -print -quit)"
[[ -z "$linked_file" ]] || {
  echo "✗ Frontend build contains symlinks; installation aborted." >&2
  exit 1
}

frontend_stage=""; backend_stage=""
promotion_started=false; completed=false
promoted=()
finish_install() {
  status=$?
  trap - EXIT HUP INT TERM
  if [[ "$completed" != true && "$promotion_started" == true ]]; then
    rollback_ok=true
    # A missing staged ROOT means the new tree was moved into webapps.
    if [[ ! -d "$frontend_stage/ROOT" && -d "$tomcat_dir/ROOT" ]]; then
      mv -T -- "$tomcat_dir/ROOT" "$frontend_stage/failed-ROOT" || rollback_ok=false
    fi
    if [[ -d "$frontend_stage/previous-ROOT" ]]; then
      mv -T -- "$frontend_stage/previous-ROOT" "$tomcat_dir/ROOT" || rollback_ok=false
    fi
    if [[ -f "$frontend_stage/previous-ROOT.war" ]]; then
      mv -T -- "$frontend_stage/previous-ROOT.war" "$tomcat_dir/ROOT.war" || rollback_ok=false
    fi
    for name in "${promoted[@]}"; do
      if [[ -f "$backend_stage/previous/$name" ]]; then
        mv -fT -- "$backend_stage/previous/$name" "$publiclink_dir/$name" || rollback_ok=false
      elif [[ -f "$publiclink_dir/$name" ]]; then
        mv -T -- "$publiclink_dir/$name" "$backend_stage/failed/$name" || rollback_ok=false
      fi
    done
    if [[ "$rollback_ok" == true ]]; then
      echo "✗ Installation failed; previous release files restored." >&2
    else
      echo "✗ Rollback incomplete; recover from the retained paths below." >&2
    fi
  elif [[ "$completed" != true ]]; then
    echo "✗ Preparation failed; live release files were not changed." >&2
  fi
  echo "  Retained frontend recovery directory: ${frontend_stage:-not created}"
  echo "  Retained backend recovery directory:  ${backend_stage:-not created}"
  exit "$status"
}
trap finish_install EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# Stage on the destination filesystem; old files remain available until ready.
# Backups are private and outside Tomcat's appBase, so they cannot be served.
frontend_stage="$(mktemp -d "$(dirname "$tomcat_dir")/.publiclink-frontend.XXXXXX")"
backend_stage="$(mktemp -d "$publiclink_dir/.publiclink-backend.XXXXXX")"
frontend_device="$(stat -c %d "$frontend_stage")"
webapps_device="$(stat -c %d "$tomcat_dir")"
if [[ "$frontend_device" != "$webapps_device" ]]; then
  echo "✗ Tomcat webapps and its parent must share a filesystem for safe renames." >&2
  exit 1
fi
mkdir "$frontend_stage/ROOT" "$backend_stage/next" "$backend_stage/previous" "$backend_stage/failed"
cp -R -- "$build_dir/." "$frontend_stage/ROOT/"
find "$frontend_stage/ROOT" -type d -exec chmod 0755 {} +
find "$frontend_stage/ROOT" -type f -exec chmod 0644 {} +
chown -R "$tomcat_user:$tomcat_group" "$frontend_stage/ROOT"
for name in "${names[@]}"; do
  test -s "$artifacts_dir/$name"
  install -o "$publiclink_user" -g "$publiclink_group" -m 0640 \
    "$artifacts_dir/$name" "$backend_stage/next/$name"
  if [[ -f "$publiclink_dir/$name" ]]; then
    cp -p -- "$publiclink_dir/$name" "$backend_stage/previous/$name"
  fi
done

promotion_started=true
for name in "${names[@]}"; do
  promoted+=("$name")
  # Rename replaces the inode, avoiding truncation of an archive in use by Java.
  mv -fT -- "$backend_stage/next/$name" "$publiclink_dir/$name"
done
if [[ -f "$tomcat_dir/ROOT.war" ]]; then
  mv -T -- "$tomcat_dir/ROOT.war" "$frontend_stage/previous-ROOT.war"
fi
if [[ -d "$tomcat_dir/ROOT" ]]; then
  mv -T -- "$tomcat_dir/ROOT" "$frontend_stage/previous-ROOT"
fi
mv -T -- "$frontend_stage/ROOT" "$tomcat_dir/ROOT"

# Direct commands work for nologin accounts; no shell/password changes needed.
runuser -u "$tomcat_user" -- test -r "$tomcat_dir/ROOT/index.html"
runuser -u "$tomcat_user" -- test -r "$tomcat_dir/ROOT/WEB-INF/web.xml"
for name in "${names[@]}"; do
  runuser -u "$publiclink_user" -- test -r "$publiclink_dir/$name"
done
completed=true
INSTALL_RELEASE

# ── Step 5: Summary ───────────────────────────────────────────────────────────
echo ""
echo "▶ Frontend installed → ${TOMCAT_DIR}/ROOT (${TOMCAT_USER}:${TOMCAT_GROUP})"
echo "▶ Backend installed  → ${PUBLICLINK_DIR} (${PUBLICLINK_USER}:${PUBLICLINK_GROUP})"
echo "▶ Local artifacts    → ${ARTIFACTS_DIR}"
echo "  Configuration outside ROOT, backend keys, and unrelated files were left unchanged."
echo "  Services were NOT restarted; endpoint health does not prove the new backend is running."
echo "  When ready, restart the services manually (chat restart disconnects active streams):"
echo "    sudo systemctl restart publiclink-gateway publiclink-app publiclink-user publiclink-chat"
echo "    sudo systemctl restart tomcat"
echo "  Recovery directories are retained; prune old backups after verifying the release."
echo "Done."
