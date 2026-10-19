import { proxy } from "@/lib/proxy";
import { NextRequest } from "next/server";
async function handler(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, (await context.params).path.map(encodeURIComponent).join("/"));
}
export { handler as DELETE, handler as GET, handler as PATCH, handler as POST, handler as PUT };
