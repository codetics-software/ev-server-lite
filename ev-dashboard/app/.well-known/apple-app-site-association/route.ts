// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

import { NextResponse } from "next/server";

export function GET() {
  const appIDs = (process.env.APPLE_APP_IDS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  if (!appIDs.length) {
    return new NextResponse(null, { status: 404 });
  }
  return NextResponse.json({
    applinks: { details: [{ appIDs, components: [{ "/": "/charge/*" }] }] },
  });
}
