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

# ---- 2006Scape repo ----
echo "--- 2006Scape ---"
if [[ -d "$ROOT/2006Scape" ]]; then
  ok_msg "2006Scape directory exists"

  has_client=false
  has_server=false

  if [[ -d "$ROOT/2006Scape/2006Scape Client" ]]; then
    ok_msg "  2006Scape Client/ present"
    has_client=true
  else
    fail_msg "  2006Scape Client/ missing"
    all_ok=false
  fi

  if [[ -d "$ROOT/2006Scape/2006Scape Server" ]]; then
    ok_msg "  2006Scape Server/ present"
    has_server=true
  else
    fail_msg "  2006Scape Server/ missing"
    all_ok=false
  fi

  if [[ -f "$ROOT/2006Scape/pom.xml" ]]; then
    ok_msg "  pom.xml present (Maven parent POM)"
  else
    fail_msg "  pom.xml missing"
    all_ok=false
  fi

  # Target / build state
  if $has_client && [[ -d "$ROOT/2006Scape/2006Scape Client/target" ]]; then
    ok_msg "  Client is already built (target/ found)"
  else
    warn_msg "  Client not yet built (run scripts/build.sh)"
  fi

  if $has_server && [[ -d "$ROOT/2006Scape/2006Scape Server/target" ]]; then
    ok_msg "  Server is already built (target/ found)"
  else
    warn_msg "  Server not yet built (run scripts/build.sh)"
  fi
else
  fail_msg "2006Scape directory missing. Clone it into $ROOT/2006Scape"
  all_ok=false
fi

# ---- Symbolic links ----
echo "--- Symlinks ---"
if [[ -L "$ROOT/server" && -d "$ROOT/server" ]]; then
  ok_msg "server -> $(readlink "$ROOT/server")"
else
  warn_msg "server symlink missing or broken (should point to 2006Scape/2006Scape Server)"
fi

if [[ -L "$ROOT/client" && -d "$ROOT/client" ]]; then
  ok_msg "client -> $(readlink "$ROOT/client")"
else
  warn_msg "client symlink missing or broken (should point to 2006Scape/2006Scape Client)"
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
