#!/bin/sh
set -eu

list="$1"
blocked="$JAVA_HOME/lib/security/blocked.certs"
expected=$(grep -cE '^[0-9A-F]{64}$' "$list")

[ "$expected" = "48" ]
[ "$(head -n 1 "$blocked")" = "Algorithm=SHA-256" ]

{
  echo "Algorithm=SHA-256"
  { tail -n +2 "$blocked"; grep -E '^[0-9A-F]{64}$' "$list"; } | LC_ALL=C sort -u
} > "$blocked.new"
cat "$blocked.new" > "$blocked"
rm "$blocked.new"

[ "$(head -n 1 "$blocked")" = "Algorithm=SHA-256" ]
[ "$(grep -cxFf "$list" "$blocked")" = "$expected" ]
