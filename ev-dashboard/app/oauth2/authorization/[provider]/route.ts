import { proxy } from "@/lib/proxy";
import { NextRequest } from "next/server";
export async function GET(
  request: NextRequest,
  context: { params: Promise<{ provider: string }> },
) {
  return proxy(
    request,
    `oauth2/authorization/${encodeURIComponent((await context.params).provider)}`,
  );
}
