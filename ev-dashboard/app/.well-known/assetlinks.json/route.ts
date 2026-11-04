// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

import { NextResponse } from "next/server";

export function GET() {
  const statements = (process.env.ANDROID_APP_LINKS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
    .map((value) => {
      const separator = value.indexOf(":");
      return {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: value.slice(0, separator),
          sha256_cert_fingerprints: [value.slice(separator + 1).toUpperCase()],
        },
      };
    });
  if (!statements.length) {
    return new NextResponse(null, { status: 404 });
  }
  return NextResponse.json(statements);
}
