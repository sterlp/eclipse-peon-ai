#!/bin/bash
# Gate G: core tests -> jar sync -> factory tests (Spring cache missCount) -> plugin compile. Stops at the first red step.
set -euo pipefail

R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
S="$R/scripts"
LOG=${GATE_LOG_DIR:-/tmp/gate}
mkdir -p "$LOG"

echo "== core clean test"
mvn -o -B -f "$R/pom.xml" -pl org.sterl.llmpeon.core clean test > "$LOG/core.log" 2>&1 \
  || { tail -40 "$LOG/core.log"; echo "core RED" >&2; exit 1; }
CORE=$(grep -E '^\[(INFO|ERROR|WARNING)\] Tests run:.*Skipped: [0-9]+$' "$LOG/core.log" | tail -1)
echo "$CORE"

echo "== core jar sync"
"$S/core-jar-sync.sh"

echo "== factory clean test"
# pnpm for the factory-ui module
export PATH="$HOME/.nvm/versions/node/v22.23.4/bin:$PATH"
mvn -o -B -f "$R/aifactory/pom.xml" -pl factory-backend clean test \
  -Dlogging.level.org.springframework.test.context.cache=DEBUG > "$LOG/factory.log" 2>&1 \
  || { tail -40 "$LOG/factory.log"; echo "factory RED" >&2; exit 1; }
FAB=$(grep -E '^\[(INFO|ERROR|WARNING)\] Tests run:.*Skipped: [0-9]+$' "$LOG/factory.log" | tail -1)
MISS1=$(grep -cE 'missCount = 1(,|\])' "$LOG/factory.log" || true)
MISSX=$(grep 'missCount = ' "$LOG/factory.log" | grep -vcE 'missCount = 1(,|\])' || true)
echo "$FAB"
echo "missCount = 1 lines: $MISS1, other missCount lines: $MISSX"
[ "$MISSX" -eq 0 ] && [ "$MISS1" -gt 0 ] || { echo "Spring context cache not single" >&2; exit 1; }

echo "== plugin compile"
PLUG=$("$S/plugin-compile.sh" "$LOG/plugin") || { echo "$PLUG"; echo "plugin RED" >&2; exit 1; }
echo "$PLUG"

echo "== SUMMARY"
echo "core:    $CORE"
echo "factory: $FAB"
echo "spring:  missCount=1 x$MISS1, other x$MISSX"
echo "plugin:  $(echo "$PLUG" | head -1)"
echo "GATE GREEN"
