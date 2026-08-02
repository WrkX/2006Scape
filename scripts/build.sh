#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="$ROOT/engine"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"

echo "==> Installing JS dependencies (pnpm)"
cd "$ROOT"
pnpm install --frozen-lockfile

echo "==> Building TypeScript content"
pnpm build:content

echo "==> Building engine (Maven)"
cd "$ENGINE_DIR"
mvn -B clean package

echo "==> Build complete"
