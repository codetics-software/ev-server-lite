import { proxy } from "@/lib/proxy";
import { NextRequest } from "next/server";
async function callback(request: NextRequest, context: { params: Promise<{ provider: string }> }) {
  return proxy(request, `login/oauth2/code/${encodeURIComponent((await context.params).provider)}`);
}
export { callback as GET, callback as POST };
