#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok_msg()   { echo -e "  ${GREEN}OK${NC}    $1"; }
fail_msg() { echo -e "  ${RED}FAIL${NC}  $1"; }
warn_msg() { echo -e "  ${YELLOW}WARN${NC} $1"; }

all_ok=true

echo "Development environment check for RS"
echo ""

# ---- Java 17 ----
echo "--- Java ---"
java_version=""
java_ok=false
if command -v java &>/dev/null; then
  java_version="$(java -version 2>&1 || true)"
  if echo "$java_version" | grep -q 'version "17'; then
    java_ok=true
  fi
fi

if $java_ok; then
  first_line="$(echo "$java_version" | head -1 || true)"
  ok_msg "Java 17 is available ($first_line)"
elif command -v /usr/libexec/java_home &>/dev/null && /usr/libexec/java_home -v 17 &>/dev/null; then
  java_version="$(/usr/libexec/java_home -v 17 --exec java -version 2>&1 || true)"
  first_line="$(echo "$java_version" | head -1 || true)"
  ok_msg "Java 17 is available ($first_line)"
else
  fail_msg "Java 17 not found. Install it (e.g. via SDKMAN or brew)"
  all_ok=false
fi

# ---- Maven ----
echo "--- Maven ---"
if command -v mvn &>/dev/null; then
  mvn_ver="$(mvn --version 2>&1 || true)"
  ok_msg "Maven is available ($(echo "$mvn_ver" | head -1 || true))"
else
  fail_msg "Maven (mvn) not found. Install it (brew install maven)"
  all_ok=false
fi

# ---- pnpm ----
echo "--- pnpm ---"
if command -v pnpm &>/dev/null; then
  ok_msg "pnpm is available ($(pnpm --version))"
else
  fail_msg "pnpm not found. Install it (npm install -g pnpm)"
  all_ok=false
fi

# ---- Engine ----
echo "--- Engine ---"
if [[ -d "$ROOT/engine" ]]; then
  ok_msg "engine directory exists"

  has_client=false
  has_server=false

  if [[ -d "$ROOT/engine/client" ]]; then
    ok_msg "  engine/client/ present"
    has_client=true
  else
    fail_msg "  engine/client/ missing"
    all_ok=false
  fi

  if [[ -d "$ROOT/engine/server" ]]; then
    ok_msg "  engine/server/ present"
    has_server=true
  else
    fail_msg "  engine/server/ missing"
    all_ok=false
  fi

  if [[ -f "$ROOT/engine/pom.xml" ]]; then
    ok_msg "  pom.xml present (Maven parent POM)"
  else
    fail_msg "  pom.xml missing"
    all_ok=false
  fi

  # Target / build state
  if $has_client && [[ -d "$ROOT/engine/client/target" ]]; then
    ok_msg "  Client is already built (target/ found)"
  else
    warn_msg "  Client not yet built (run scripts/build.sh)"
  fi

  if $has_server && [[ -d "$ROOT/engine/server/target" ]]; then
    ok_msg "  Server is already built (target/ found)"
  else
    warn_msg "  Server not yet built (run scripts/build.sh)"
  fi
else
  fail_msg "engine directory missing. Import the Java engine into $ROOT/engine"
  all_ok=false
fi

# ---- Workspace path audit ----
echo "--- Workspace paths ---"
if rg -n \
  'engine/2006Scape Client|engine/2006Scape Server|engine/content|2006Scape/content|\$ROOT/2006Scape|workspaceFolder/2006Scape' \
  "$ROOT/scripts" "$ROOT/.vscode" "$ROOT/docs" "$ROOT/engine" \
  --glob '!dev-check.sh' \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  --glob '!**/dist/**' >/dev/null 2>&1; then
  fail_msg "obsolete workspace path references found"
  all_ok=false
else
  ok_msg "no obsolete workspace path references found"
fi

echo ""
echo "--- Summary ---"
if $all_ok; then
  echo -e "  ${GREEN}Everything looks ready.${NC}"
  exit 0
else
  echo -e "  ${RED}Some items are missing or broken.${NC}"
  exit 1
fi
