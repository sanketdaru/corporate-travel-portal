"use client";

import { useDelegationContext } from "@/lib/context/DelegationContext";

export function DelegationBanner() {
  const { delegationActive, actorName, subjectName, delegationId, consentId, purpose, expiresAt, exitDelegation } =
    useDelegationContext();

  if (!delegationActive) return null;

  return (
    <div
      role="alert"
      className="fixed top-14 left-0 right-0 z-40 h-10 bg-amber-50 border-b border-amber-200 flex items-center justify-between px-6"
    >
      <div className="flex items-center gap-3 min-w-0">
        {/* Icon + primary label */}
        <div className="flex items-center gap-1.5 text-amber-700 flex-shrink-0">
          <svg
            className="w-3.5 h-3.5 text-amber-500"
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
          <span className="text-xs font-semibold">
            {actorName} is acting on behalf of {subjectName}
          </span>
        </div>

        <span className="w-px h-3.5 bg-amber-300 flex-shrink-0" />

        <span className="text-xs text-amber-800 truncate">
          Delegation active — all actions attributed to {subjectName}
        </span>

        {(delegationId || purpose || consentId || expiresAt) && (
          <>
            <span className="w-px h-3.5 bg-amber-300 flex-shrink-0 hidden lg:block" />
            <span className="text-xs text-amber-600 font-mono truncate hidden lg:block">
              {[delegationId, purpose, consentId, expiresAt && `Expires ${expiresAt}`]
                .filter(Boolean)
                .join(" · ")}
            </span>
          </>
        )}
      </div>

      <button
        onClick={exitDelegation}
        className="flex-shrink-0 flex items-center gap-1 text-xs font-medium text-amber-700 hover:text-amber-900 transition-colors ml-4"
      >
        <svg
          className="w-3.5 h-3.5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M6 18L18 6M6 6l12 12"
          />
        </svg>
        Exit delegation
      </button>
    </div>
  );
}
