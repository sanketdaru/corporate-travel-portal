"use client";

import { signIn } from "next-auth/react";

export default function LoginPage() {
  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-blue-600 rounded-2xl shadow-lg mb-5">
            <svg
              className="w-7 h-7 text-white"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
              />
            </svg>
          </div>
          <h1 className="text-xl font-semibold text-slate-900">TravelCorp</h1>
          <p className="text-sm text-slate-400 mt-1">
            Corporate Travel &amp; Expense Platform
          </p>
        </div>

        {/* Card */}
        <div className="bg-white border border-slate-200 rounded-2xl shadow-sm p-8">
          <h2 className="text-base font-semibold text-slate-900 mb-1">
            Sign in to your account
          </h2>
          <p className="text-sm text-slate-400 mb-6">
            Use your corporate SSO credentials
          </p>

          <button
            type="button"
            onClick={() => signIn("keycloak", { callbackUrl: "/dashboard" })}
            className="w-full flex items-center justify-center gap-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium py-2.5 px-4 rounded-lg transition-colors"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
              />
            </svg>
            Continue with Corporate SSO
          </button>

          <p className="text-center text-xs text-slate-400 mt-6">
            Powered by Keycloak OIDC
          </p>
        </div>
      </div>
    </div>
  );
}
