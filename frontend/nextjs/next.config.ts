import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return {
      // beforeFiles: run BEFORE the App Router filesystem check so that
      // /api/bff/* and /api/{bookings,expenses,delegations,consents}/* are
      // proxied to the backend before Next.js can return a 404 for unknown routes.
      beforeFiles: [
        // BFF (Employee BFF, port 8085)
        {
          source: "/api/bff/:path*",
          destination: "http://localhost:8085/api/bff/:path*",
        },
        // API Gateway (port 8000) — explicit paths only; /api/auth/* is reserved for NextAuth
        {
          source: "/api/bookings/:path*",
          destination: "http://localhost:8000/api/bookings/:path*",
        },
        {
          source: "/api/expenses/:path*",
          destination: "http://localhost:8000/api/expenses/:path*",
        },
        {
          source: "/api/delegations/:path*",
          destination: "http://localhost:8000/api/delegations/:path*",
        },
        {
          source: "/api/consents/:path*",
          destination: "http://localhost:8000/api/consents/:path*",
        },
      ],
      afterFiles: [],
      fallback: [],
    };
  },
};

export default nextConfig;
