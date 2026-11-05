import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // خطاهای تایپ دیگر نادیده گرفته نمی‌شوند تا باگ‌ها در build لو بروند
  typescript: {
    ignoreBuildErrors: false,
  },
  reactStrictMode: false,
};

export default nextConfig;
