#!/bin/bash
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

set -u
root="$(cd "$(dirname "$0")/.." && pwd)"
status=0
attribution="EV Supervision Lite — built by Kevin Vavelin, Codetics"

for file in LICENSE NOTICE AGENTS.md; do
  [ -f "$root/$file" ] || { echo "Missing $file"; status=1; }
done
grep -q "LicenseRef-Codetics-Attribution-1.0" "$root/LICENSE" || { echo "LICENSE was altered"; status=1; }
grep -q "$attribution" "$root/LICENSE" || { echo "LICENSE lost the attribution"; status=1; }

for visible in \
  "ev-app-android/app/src/main/java/fr/codetics/evapp/features/account/AccountScreens.kt" \
  "ev-app-ios/EV App/EV App/Features/Account/AboutScreen.swift"; do
  if [ -f "$root/$visible" ]; then
    grep -q "$attribution" "$root/$visible" || { echo "Visible attribution missing in $visible"; status=1; }
    grep -q "https://codetics.fr" "$root/$visible" || { echo "Visible link missing in $visible"; status=1; }
  fi
done

while IFS= read -r -d '' source; do
  head -n 8 "$source" | grep -q "https://codetics.fr" || { echo "Licence header missing in ${source#"$root"/}"; status=1; }
done < <(find "$root/ev-app-android/app/src" "$root/ev-app-android/scripts" "$root/ev-app-ios" \
  -type f \( -name '*.kt' -o -name '*.swift' -o -name '*.java' -o -name '*.py' -o -name '*.sh' \) -print0)

[ "$status" = 0 ] && echo "Licence and attribution are in place."
exit $status
