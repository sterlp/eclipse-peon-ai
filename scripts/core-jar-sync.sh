#!/bin/bash
# Installs the core offline and copies its jar into the plugin lib (delete + copy), verifying the SHA-256.
set -euo pipefail

R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$R/org.sterl.llmpeon.core/target/llmpeon-core-2.8.1-SNAPSHOT.jar"
DST="$R/org.sterl.llmpeon/lib/llmpeon-core.jar"

mvn -o -B -q -f "$R/pom.xml" -pl org.sterl.llmpeon.core install -DskipTests
rm -f "$DST"
cp "$SRC" "$DST"
A=$(shasum -a 256 "$SRC" | cut -d' ' -f1)
B=$(shasum -a 256 "$DST" | cut -d' ' -f1)
echo "sha256 source: $A"
echo "sha256 target: $B"
[ "$A" = "$B" ] || { echo "SHA mismatch" >&2; exit 1; }
echo "jar sync ok"
