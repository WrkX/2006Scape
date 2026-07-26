#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}"

JAR="$ROOT/2006Scape/2006Scape Client/target/client-1.0-jar-with-dependencies.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Client JAR not found. Run scripts/build.sh first." >&2
  exit 1
fi

echo "==> Starting 2006Scape client (local mode)..."
echo "    NOTE: Do NOT press Escape on the login screen (known bug)."
echo ""

# -local connects to localhost and skips CRC checks (see Main.java)
JAVA_OPTS=()
if [[ "$(uname -s)" == "Darwin" ]]; then
  # Mitigates Retina mouse/rendering offset in legacy AWT clients on macOS
  JAVA_OPTS+=(-Dsun.java2d.uiScale=1)
fi

exec java "${JAVA_OPTS[@]}" -jar "$JAR" -local "$@"
