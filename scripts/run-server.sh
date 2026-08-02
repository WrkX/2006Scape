#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="$ROOT/engine"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"
JAVA_BIN="$JAVA_HOME/bin/java"

SERVER_DIR="$ENGINE_DIR/server"
CONTENT_DIR="${SINGLESCAPE_CONTENT_DIR:-$ROOT/content/dist}"
JAR="$SERVER_DIR/target/server-1.0.jar"
LOCAL_LIB_DIR="$SERVER_DIR/libs"
LIB_DIR="$SERVER_DIR/target/lib"

if [[ ! -x "$JAVA_BIN" ]]; then
  echo "Java runtime not found at $JAVA_BIN." >&2
  exit 1
fi

if [[ ! -f "$JAR" || ! -d "$LOCAL_LIB_DIR" || ! -d "$LIB_DIR" ]]; then
  echo "Server runtime not found. Run scripts/build.sh first." >&2
  exit 1
fi

if [[ ! -f "$CONTENT_DIR/loader.js" ]]; then
  echo "Built content loader not found at $CONTENT_DIR/loader.js. Run scripts/build.sh first or set SINGLESCAPE_CONTENT_DIR." >&2
  exit 1
fi

export SINGLESCAPE_CONTENT_DIR="$CONTENT_DIR"

echo "==> Starting SingleScape server..."
echo "    Script content: $SINGLESCAPE_CONTENT_DIR"
echo "    Server address: http://localhost:43594"
echo ""

cd "$SERVER_DIR"
# target/lib must come before libs/: libs/google-collect-1.0.jar ships an ancient
# com.google.common.base.Platform that shadows Guava 19 and kills the game tick
# with NoSuchMethodError on Stopwatch.createStarted() (silent — Error bypasses
# catch (Exception), so the scheduler stops and clients only see Connection lost).
exec "$JAVA_BIN" -cp "$JAR:$LIB_DIR/*:$LOCAL_LIB_DIR/*" com.rs2.GameEngine "$@"
