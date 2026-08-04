#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="$ROOT/engine"
CLIENT_DIR="$ENGINE_DIR/client"
DIST_DIR="$CLIENT_DIR/target/dist"
INPUT_DIR="$CLIENT_DIR/target/jpackage-input"

APP_NAME="2006Scape"
APP_VERSION="1.0"
VENDOR="2006Scape"
DESCRIPTION="2006Scape desktop game client"
MAIN_JAR="client-${APP_VERSION}-jar-with-dependencies.jar"
ICON="$CLIENT_DIR/src/main/resources/client-icon.png"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a JDK 17+ installation with jpackage." >&2
  exit 1
fi

if ! command -v "$JAVA_HOME/bin/jpackage" >/dev/null 2>&1; then
  echo "jpackage was not found in $JAVA_HOME/bin." >&2
  exit 1
fi

echo "==> Building client JAR with Java ${maven.compiler.release:-17}..."
(cd "$ENGINE_DIR" && ./mvnw -B -pl client -am clean package)

mkdir -p "$INPUT_DIR"
cp "$CLIENT_DIR/target/$MAIN_JAR" "$INPUT_DIR/"

JPACKAGE_ARGS=(
  --input "$INPUT_DIR"
  --main-jar "$MAIN_JAR"
  --main-class Main
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "$VENDOR"
  --description "$DESCRIPTION"
  --dest "$DIST_DIR"
  --java-options "-Dfile.encoding=UTF-8"
)

if [[ -f "$ICON" ]]; then
  JPACKAGE_ARGS+=(--icon "$ICON")
fi

OS="$(uname -s)"
case "$OS" in
  Darwin)
    JPACKAGE_ARGS+=(--type dmg)
    ;;
  Linux)
    JPACKAGE_ARGS+=(--type app-image)
    ;;
  MINGW*|MSYS*|CYGWIN*)
    JPACKAGE_ARGS+=(--type exe --win-console)
    ;;
  *)
    echo "Unsupported packaging platform: $OS" >&2
    exit 1
    ;;
esac

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

echo "==> Creating desktop package with jpackage..."
"$JAVA_HOME/bin/jpackage" "${JPACKAGE_ARGS[@]}"

echo "==> Package created in $DIST_DIR"
