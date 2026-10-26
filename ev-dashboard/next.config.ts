import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  experimental: { agentFeedback: true },
  devIndicators: false,
};

export default nextConfig;
