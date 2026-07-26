#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"
JAVA_BIN="$JAVA_HOME/bin/java"

SERVER_DIR="$ROOT/2006Scape/2006Scape Server"
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

echo "==> Starting 2006Scape server..."
echo "    Server address: http://localhost:43594"
echo ""

cd "$SERVER_DIR"
# target/lib must come before libs/: libs/google-collect-1.0.jar ships an ancient
# com.google.common.base.Platform that shadows Guava 19 and kills the game tick
# with NoSuchMethodError on Stopwatch.createStarted() (silent — Error bypasses
# catch (Exception), so the scheduler stops and clients only see Connection lost).
exec "$JAVA_BIN" -cp "$JAR:$LIB_DIR/*:$LOCAL_LIB_DIR/*" com.rs2.GameEngine "$@"
