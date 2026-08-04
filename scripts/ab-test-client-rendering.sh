#!/usr/bin/env bash
# A/B test: RS client with original 2006Scape rendering files vs current QoL client.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="$ROOT/engine"
CLIENT_SRC="$ENGINE_DIR/client/src/main/java"
ORIGINAL_SRC="/Users/jonas/Downloads/2006Scape-master/2006Scape Client/src/main/java"
BACKUP_DIR="/tmp/rs-client-ab-backup-$$"
OUT_DIR="$ROOT/engine/client/target/ab-test"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"

FILES=(
  Background.java
  ClientSettings.java
  Game.java
  ItemDef.java
  Main.java
  RSApplet.java
  RSFrame.java
  RSImageProducer.java
  Texture.java
)

restore_client_sources() {
  if [[ -d "$BACKUP_DIR" ]]; then
    for file in "${FILES[@]}" ClientPreferences.java; do
      if [[ -f "$BACKUP_DIR/$file" ]]; then
        cp "$BACKUP_DIR/$file" "$CLIENT_SRC/$file"
      elif [[ "$file" == "ClientPreferences.java" && -f "$BACKUP_DIR/$file.missing" ]]; then
        rm -f "$CLIENT_SRC/ClientPreferences.java"
      fi
    done
    rm -rf "$BACKUP_DIR"
  fi
}

trap restore_client_sources EXIT

build_client_variant() {
  local label="$1"
  (cd "$ENGINE_DIR" && mvn -q -pl client -am package -DskipTests)
  mkdir -p "$OUT_DIR"
  cp "$ENGINE_DIR/client/target/client-1.0-jar-with-dependencies.jar" "$OUT_DIR/client-${label}.jar"
  echo "Built $OUT_DIR/client-${label}.jar"
}

echo "==> Backing up current client sources..."
mkdir -p "$BACKUP_DIR"
for file in "${FILES[@]}"; do
  cp "$CLIENT_SRC/$file" "$BACKUP_DIR/$file"
done
if [[ -f "$CLIENT_SRC/ClientPreferences.java" ]]; then
  cp "$CLIENT_SRC/ClientPreferences.java" "$BACKUP_DIR/ClientPreferences.java"
else
  touch "$BACKUP_DIR/ClientPreferences.java.missing"
fi

echo "==> Building variant B (current RS client)..."
build_client_variant "B-current"

echo "==> Swapping in original 2006Scape rendering sources..."
for file in "${FILES[@]}"; do
  cp "$ORIGINAL_SRC/$file" "$CLIENT_SRC/$file"
done
rm -f "$CLIENT_SRC/ClientPreferences.java"

echo "==> Building variant A (original rendering)..."
build_client_variant "A-original"

echo ""
echo "==> A/B jars ready:"
ls -lh "$OUT_DIR"/client-*.jar
echo ""
echo "Run server first, then:"
echo "  java -jar $OUT_DIR/client-A-original.jar -local      # original rendering"
echo "  java -jar $OUT_DIR/client-B-current.jar -local      # current RS client"
echo ""
echo "Sources will be restored automatically on exit."
