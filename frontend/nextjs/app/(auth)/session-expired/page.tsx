"use client";

import { useEffect, useState } from "react";
import { signOut } from "next-auth/react";

const COUNTDOWN_SECONDS = 5;

export default function SessionExpiredPage() {
  const [secondsLeft, setSecondsLeft] = useState(COUNTDOWN_SECONDS);

  useEffect(() => {
    // Clear the NextAuth session cookie and end the Keycloak SSO session
    // silently (no redirect — this page handles navigation itself).
    signOut({ redirect: false });
  }, []);

  useEffect(() => {
    if (secondsLeft <= 0) {
      window.location.href = "/login";
      return;
    }
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm text-center">

        {/* Logo */}
        <div className="inline-flex items-center justify-center w-14 h-14 bg-blue-600 rounded-2xl shadow-lg mb-5">
          <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
          </svg>
        </div>

        <h1 className="text-xl font-semibold text-slate-900 mb-1">TravelCorp</h1>
        <p className="text-sm text-slate-400 mb-8">Corporate Travel &amp; Expense Platform</p>

        {/* Card */}
        <div className="bg-white border border-slate-200 rounded-2xl shadow-sm p-8">
          {/* Lock icon */}
          <div className="inline-flex items-center justify-center w-12 h-12 bg-amber-50 rounded-xl mb-5">
            <svg className="w-6 h-6 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>

          <h2 className="text-base font-semibold text-slate-900 mb-2">
            Your session has expired
          </h2>
          <p className="text-sm text-slate-500 mb-6">
            You were automatically signed out because your session was inactive for too long.
            Please sign in again to continue.
          </p>

          {/* Countdown */}
          <div className="flex items-center justify-center gap-2 text-sm text-slate-400 mb-6">
            <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Redirecting to login in{" "}
            <span className="font-semibold text-slate-700 tabular-nums w-3 inline-block text-center">
              {secondsLeft}
            </span>{" "}
            second{secondsLeft !== 1 ? "s" : ""}…
          </div>

          {/* Progress bar */}
          <div className="w-full bg-slate-100 rounded-full h-1 mb-6 overflow-hidden">
            <div
              className="bg-blue-500 h-1 rounded-full transition-all duration-1000 ease-linear"
              style={{ width: `${((COUNTDOWN_SECONDS - secondsLeft) / COUNTDOWN_SECONDS) * 100}%` }}
              role="progressbar"
              aria-valuenow={COUNTDOWN_SECONDS - secondsLeft}
              aria-valuemin={0}
              aria-valuemax={COUNTDOWN_SECONDS}
            />
          </div>

          <button
            type="button"
            onClick={() => { window.location.href = "/login"; }}
            className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium py-2.5 px-4 rounded-lg transition-colors"
          >
            Go to Login now
          </button>
        </div>

        <p className="text-xs text-slate-400 mt-6">
          Your work is safe — no changes were lost.
        </p>
      </div>
    </div>
  );
}
