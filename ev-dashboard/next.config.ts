import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  experimental: { agentFeedback: true },
  devIndicators: false,
};

export default nextConfig;
