#!/bin/bash
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$root/deploy/certificates}"
organization="${ISO15118_ORGANIZATION:-EV Supervision Lite TEST}"
force="${ISO15118_FORCE:-false}"

command -v openssl >/dev/null || { echo "openssl est requis."; exit 1; }
mkdir -p "$out"
for file in v2g-root.pem cpo-sub-ca.pem cpo-sub-ca.key mo-root.pem; do
  if [ -e "$out/$file" ] && [ "$force" != "true" ]; then
    echo "$out/$file existe déjà. Relancez avec ISO15118_FORCE=true pour le remplacer."
    exit 1
  fi
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

key() {
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1"
}

authority() {
  local name="$1" subject="$2" days="$3" issuer="$4" pathlen="$5"
  key "$work/$name.key"
  printf 'basicConstraints=critical,CA:true%s\nkeyUsage=critical,keyCertSign,cRLSign\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid\n' "$pathlen" > "$work/$name.ext"
  if [ "$issuer" = "self" ]; then
    openssl req -x509 -new -key "$work/$name.key" -sha256 -days "$days" -subj "$subject" \
      -addext "basicConstraints=critical,CA:true$pathlen" -addext "keyUsage=critical,keyCertSign,cRLSign" \
      -out "$work/$name.pem"
  else
    openssl req -new -key "$work/$name.key" -subj "$subject" -out "$work/$name.csr"
    openssl x509 -req -in "$work/$name.csr" -CA "$work/$issuer.pem" -CAkey "$work/$issuer.key" -CAcreateserial \
      -sha256 -days "$days" -extfile "$work/$name.ext" -out "$work/$name.pem" 2>/dev/null
  fi
}

authority v2g-root "/CN=V2G Root CA/O=$organization" 3650 self ""
authority cpo-sub-1 "/CN=CPO Sub-CA 1/O=$organization" 1825 v2g-root ",pathlen:1"
authority cpo-sub-2 "/CN=CPO Sub-CA 2/O=$organization" 730 cpo-sub-1 ",pathlen:0"
authority mo-root "/CN=MO Root CA/O=$organization" 3650 self ""

openssl verify -CAfile "$work/v2g-root.pem" -untrusted "$work/cpo-sub-1.pem" "$work/cpo-sub-2.pem" >/dev/null

cp "$work/v2g-root.pem" "$out/v2g-root.pem"
cat "$work/cpo-sub-2.pem" "$work/cpo-sub-1.pem" > "$out/cpo-sub-ca.pem"
cp "$work/cpo-sub-2.key" "$out/cpo-sub-ca.key"
cp "$work/mo-root.pem" "$out/mo-root.pem"
chmod 600 "$out/cpo-sub-ca.key"
chmod 644 "$out/v2g-root.pem" "$out/cpo-sub-ca.pem" "$out/mo-root.pem"

echo "Certificats ISO 15118 créés dans $out :"
for file in v2g-root.pem cpo-sub-ca.pem mo-root.pem; do
  echo " - $file : $(openssl x509 -in "$out/$file" -noout -subject -enddate | tr '\n' ' ')"
done
echo " - cpo-sub-ca.key : clé privée de la sous-autorité CPO 2 (à protéger)"
