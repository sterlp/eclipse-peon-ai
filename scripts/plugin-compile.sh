#!/bin/bash
# Compiles plugin and plugin-test sources with javac against the local Eclipse plugins (no Tycho).
# Usage: scripts/plugin-compile.sh [outputDir]   env: ECLIPSE_PLUGINS
set -euo pipefail

R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E=${ECLIPSE_PLUGINS:-/Applications/Eclipse.app/Contents/Eclipse/plugins}
OUT=${1:-/tmp/plugin-compile}
[ -d "$E" ] || { echo "ECLIPSE_PLUGINS not found: $E" >&2; exit 2; }

rm -rf "$OUT"; mkdir -p "$OUT/p" "$OUT/t"
DIRJARS=$(for d in "$E"/*/; do [ -d "$d" ] && ls "$d"*.jar 2>/dev/null || true; done | tr '\n' ':')
DIRS=$(for d in "$E"/*/; do [ -d "$d" ] && echo -n "${d%/}:"; done)
CP=$(ls "$E"/*.jar | grep -v '\.source_' | tr '\n' ':')$DIRJARS$DIRS$(ls "$R"/org.sterl.llmpeon/lib/*.jar | tr '\n' ':')

find "$R/org.sterl.llmpeon/src" -name '*.java' > "$OUT/p.txt"
find "$R/org.sterl.llmpeon.test/src" -name '*.java' > "$OUT/t.txt"
echo "plugin sources: $(wc -l < "$OUT/p.txt" | tr -d ' ')  test sources: $(wc -l < "$OUT/t.txt" | tr -d ' ')"

FAIL=0
compile() { # label, classpath, srcList, outDir, log
  local rc=0
  javac --release 21 -encoding UTF-8 -proc:none -nowarn -Xmaxerrs 1000 -cp "$2" -d "$4" @"$3" > "$5" 2>&1 || rc=$?
  local errs; errs=$(grep -cE ': (error|Fehler):' "$5" || true)
  echo "$1 exit=$rc"
  echo "$1 errors: $errs"
  if [ "$rc" -ne 0 ] || [ "$errs" -ne 0 ]; then FAIL=1; fi
}
compile plugin "$CP" "$OUT/p.txt" "$OUT/p" "$OUT/p.log"
compile test "$OUT/p:$CP" "$OUT/t.txt" "$OUT/t" "$OUT/t.log"
exit $FAIL
