#!/usr/bin/env bash
# Capture baseline client screenshots at the modernization plan resolutions.
#
# Prerequisites:
#   - A running local server (java -jar server jar, or ./run-server)
#   - Client cache files in the default signlink location
#
# Usage:
#   scripts/capture-client-baselines.sh [repo-root]
#
# For each resolution the script launches the client, waits for the login
# screen, and saves a screenshot into engine/client/baselines/<WxH>/login.png.
# Additional scenarios (fixed-mode, resizable panels, etc.) can be added later.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ $# -gt 0 ]]; then
  ROOT="$(cd "$1" && pwd)"
fi

ENGINE_DIR="$ROOT/engine"
OUT_ROOT="$ENGINE_DIR/client/baselines"
JAR="$ENGINE_DIR/client/target/client-1.0-jar-with-dependencies.jar"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || command -v java)}"

echo "==> Building client..."
(cd "$ENGINE_DIR" && ./mvnw -q -pl client -am package -DskipTests)

if [[ ! -f "$JAR" ]]; then
  echo "Client jar not found: $JAR" >&2
  exit 1
fi

mkdir -p "$OUT_ROOT"

declare -a RESOLUTIONS=(
  "765x503"
  "1024x768"
  "1280x720"
  "1920x1080"
  "2560x1440"
)

echo "==> Baseline capture directories:"
for resolution in "${RESOLUTIONS[@]}"; do
  mkdir -p "$OUT_ROOT/$resolution"
  echo "  $OUT_ROOT/$resolution/"
done

cat <<EOF

Manual capture workflow (automated headless capture is not yet wired):
  1. Start the server locally.
  2. For each resolution below, resize the client window and press the in-game
     screenshot key (or use the settings tab) while on the login screen.
  3. Copy the saved PNG into the matching directory as login.png.

Resolutions:
EOF

for resolution in "${RESOLUTIONS[@]}"; do
  echo "  - $resolution -> $OUT_ROOT/$resolution/login.png"
done

echo ""
echo "Launching client for the first baseline (765x503, fixed mode)..."
echo "Close the client window when finished capturing."
java -jar "$JAR" -local -no-resize -screenshots
