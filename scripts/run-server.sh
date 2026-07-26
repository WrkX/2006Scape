#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"

JAR="$ROOT/2006Scape/2006Scape Server/target/server-1.0-jar-with-dependencies.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Server JAR not found. Run scripts/build.sh first." >&2
  exit 1
fi

echo "==> Starting 2006Scape server..."
echo "    Server address: http://localhost:43594"
echo ""

cd "$ROOT/2006Scape/2006Scape Server"
exec java -jar "$JAR" "$@"
