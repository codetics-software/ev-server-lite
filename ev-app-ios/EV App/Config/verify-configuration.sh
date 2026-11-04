#!/bin/bash
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

set -u

root="${SRCROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
configuration="${CONFIGURATION:-Release}"
status=0

report() {
  if [ "$configuration" = "Release" ]; then
    echo "error: $1"
    status=1
  else
    echo "warning: $1"
  fi
}

for file in "$root/EV App/AppConfiguration.plist" "$root/Config/EVApp.entitlements"; do
  if [ ! -f "$file" ]; then
    echo "error: Missing file $file"
    exit 1
  fi
  while IFS= read -r placeholder; do
    [ -n "$placeholder" ] && report "Placeholder $placeholder remains in ${file#"$root"/}. Fill it in before publishing."
  done < <(grep -oE '__[A-Z0-9_]+__' "$file" | sort -u)
done

if [ "$configuration" = "Release" ]; then
  for key in API_URL PORTAL_URL PRIVACY_POLICY_URL; do
    value=$(/usr/libexec/PlistBuddy -c "Print :$key" "$root/EV App/AppConfiguration.plist" 2>/dev/null || true)
    case "$value" in
      https://*) ;;
      *) report "$key must be an https:// address in EV App/AppConfiguration.plist." ;;
    esac
  done
fi

while IFS= read -r source; do
  if ! head -n 6 "$source" | grep -q "https://codetics.fr"; then
    echo "error: Licence header missing in ${source#"$root"/}"
    status=1
  fi
done < <(find "$root/EV App" "$root/EV AppTests" "$root/EV AppUITests" -name '*.swift' -type f)

exit $status
