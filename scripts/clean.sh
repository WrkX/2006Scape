#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Removing content/dist/"
rm -rf "$ROOT/content/dist"

echo "==> Removing engine build artifacts (target/)"
find "$ROOT/engine" -type d -name target -exec rm -rf {} + 2>/dev/null || true

if [[ -d "$ROOT/node_modules" ]]; then
  echo ""
  echo "==> node_modules/ exists. Remove it? [y/N]"
  read -r answer
  if [[ "$answer" =~ ^[yY](es)?$ ]]; then
    echo "==> Removing node_modules/"
    rm -rf "$ROOT/node_modules"
    echo "==> Removing .pnpm-store/"
    rm -rf "$ROOT/.pnpm-store"
  else
    echo "==> Skipping node_modules/"
  fi
fi

echo "==> Clean complete"
