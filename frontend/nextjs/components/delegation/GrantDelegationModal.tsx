"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import { createDelegation, createConsent, revokeDelegation } from "@/lib/api/gateway";

// Known users in the tenant. In a production system this would come from a
// users API; for now we use the fixed set of test users.
const KNOWN_USERS = [
  { id: "alice.employee",  displayName: "Alice Employee",  email: "alice.employee@acme-corp" },
  { id: "bob.manager",     displayName: "Bob Manager",     email: "bob.manager@acme-corp" },
  { id: "carol.executive", displayName: "Carol Executive", email: "carol.executive@acme-corp" },
  { id: "dave.assistant",  displayName: "Dave Assistant",  email: "dave.assistant@acme-corp" },
];

const PURPOSES = ["book_travel", "approve_expenses", "view_reports"] as const;
const SCOPES   = ["view_bookings", "create_bookings", "view_expenses", "create_expenses"] as const;

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  /** Display name of the currently signed-in user, used to exclude self. */
  currentUserName?: string;
}

export function GrantDelegationModal({ open, onClose, onSuccess, currentUserName }: Props) {
  const [delegateId, setDelegateId] = useState("");
  const [purpose,    setPurpose]    = useState("book_travel");
  const [scopes,     setScopes]     = useState<string[]>(["view_bookings", "create_bookings"]);
  const [expiresAt,  setExpiresAt]  = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error,      setError]      = useState<string | null>(null);

  // Reset form and error every time the modal opens so stale state never leaks.
  useEffect(() => {
    if (open) {
      setDelegateId("");
      setPurpose("book_travel");
      setScopes(["view_bookings", "create_bookings"]);
      setExpiresAt("");
      setError(null);
    }
  }, [open]);

  if (!open) return null;

  // Exclude the currently signed-in user from the delegate options.
  const delegates = KNOWN_USERS.filter((u) => u.displayName !== currentUserName);
  const selectedDelegate = KNOWN_USERS.find((u) => u.id === delegateId);

  function toggleScope(scope: string) {
    setScopes((prev) =>
      prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope]
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!delegateId || scopes.length === 0) return;

    setSubmitting(true);
    setError(null);
    try {
      // datetime-local gives "yyyy-MM-ddTHH:mm" (16 chars).
      // Backend @JsonFormat expects "yyyy-MM-dd'T'HH:mm:ss" — just append seconds.
      const isoExpiry = expiresAt ? expiresAt.slice(0, 16) + ":00" : undefined;

      // Step 1 — create the delegation record.
      const delegation = await createDelegation({
        delegateId,
        purpose,
        scopes,
        ...(isoExpiry ? { expiresAt: isoExpiry } : {}),
      });

      // Step 2 — create the matching consent record.
      // The BFF validates consent existence before performing token exchange,
      // so both records must exist for activation to succeed.
      try {
        await createConsent({
          grantorId:    delegation.delegatorId,
          granteeId:    delegation.delegateId,
          delegationId: delegation.id,
          purpose,
          scopes,
          ...(isoExpiry ? { expiresAt: isoExpiry } : {}),
        });
      } catch (consentErr) {
        // A 400 "Active consent already exists" is not a real failure.
        // The BFF validates consent by grantor+grantee+purpose+scopes — not by
        // delegationId — so an existing active consent from a prior delegation
        // will still allow this new delegation to be activated.
        const isDuplicate =
          axios.isAxiosError(consentErr) &&
          consentErr.response?.status === 400 &&
          (consentErr.response.data?.detail as string | undefined)
            ?.toLowerCase()
            .includes("already exists");

        if (isDuplicate) {
          // Existing consent covers this delegation — proceed normally.
          onSuccess();
          return;
        }

        // Any other consent failure is a real problem. Roll back the delegation
        // so the user is not left with an orphan that can never be activated.
        try {
          await revokeDelegation(delegation.id);
        } catch {
          // Rollback failed — surface the delegation ID so an admin can clean up.
          setError(
            `Consent could not be created and the delegation could not be automatically removed. ` +
            `Please ask an administrator to revoke delegation ${delegation.id}.`
          );
          return;
        }

        const detail =
          axios.isAxiosError(consentErr) && consentErr.response?.data?.detail
            ? consentErr.response.data.detail
            : "An unexpected error occurred creating the consent record.";
        setError(`${detail} The delegation was automatically removed — please try again.`);
        return;
      }

      onSuccess();
    } catch (err) {
      const detail =
        axios.isAxiosError(err) && err.response?.data?.detail
          ? err.response.data.detail
          : "Failed to create delegation. Please check your connection and try again.";
      setError(detail);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="grant-modal-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h3 id="grant-modal-title" className="text-base font-semibold text-slate-900">
              Grant New Delegation
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">Allow a colleague to act on your behalf</p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close modal"
            className="text-slate-400 hover:text-slate-600 transition-colors p-1 rounded-lg hover:bg-slate-100"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="px-6 py-5 space-y-4">
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 text-xs text-red-700">
                {error}
              </div>
            )}

            {/* Delegate select */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Delegate <span className="text-red-400">*</span>
              </label>
              <select
                required
                value={delegateId}
                onChange={(e) => setDelegateId(e.target.value)}
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="">Select a colleague…</option>
                {delegates.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.displayName} ({u.email})
                  </option>
                ))}
              </select>
            </div>

            {/* Purpose */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Purpose <span className="text-red-400">*</span>
              </label>
              <select
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                {PURPOSES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>

            {/* Scopes */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                Scopes <span className="text-red-400">*</span>
              </label>
              <div className="space-y-2.5">
                {SCOPES.map((scope) => (
                  <label key={scope} className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={scopes.includes(scope)}
                      onChange={() => toggleScope(scope)}
                      className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-sm text-slate-700">{scope}</span>
                  </label>
                ))}
              </div>
              {scopes.length === 0 && (
                <p className="text-xs text-red-500 mt-1.5">At least one scope is required.</p>
              )}
            </div>

            {/* Expiry */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Expiry</label>
              <input
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
              <p className="text-xs text-slate-400 mt-1">
                Leave blank for no expiry. A consent record will be created automatically.
              </p>
            </div>

            {/* Summary preview */}
            <div className="border border-amber-200 rounded-lg overflow-hidden">
              <div className="bg-amber-50 px-3.5 py-2 border-b border-amber-200">
                <p className="text-xs font-semibold text-amber-900">This will create</p>
              </div>
              <div className="divide-y divide-amber-100">

                {/* Row 1 — Delegation record */}
                <div className="bg-amber-50/40 px-3.5 py-3 flex items-start gap-3">
                  <div className="w-6 h-6 rounded-md bg-amber-100 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <svg className="w-3.5 h-3.5 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                    </svg>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-amber-900 mb-1.5">Delegation record</p>
                    <div className="flex flex-wrap items-center gap-1.5 text-xs text-amber-800">
                      <span className="font-mono bg-white border border-amber-200 px-1.5 py-0.5 rounded">
                        {currentUserName ?? "you"}
                      </span>
                      <svg className="w-3 h-3 text-amber-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                      </svg>
                      <span className="font-mono bg-white border border-amber-200 px-1.5 py-0.5 rounded">
                        {selectedDelegate?.id ?? "select a delegate"}
                      </span>
                      <span className="text-amber-400">·</span>
                      <span className="font-mono bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                        {purpose}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Row 2 — Consent record */}
                <div className="bg-amber-50/40 px-3.5 py-3 flex items-start gap-3">
                  <div className="w-6 h-6 rounded-md bg-amber-100 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <svg className="w-3.5 h-3.5 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
                    </svg>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-amber-900 mb-1.5">
                      Consent record{" "}
                      <span className="font-normal text-amber-700">— required for activation</span>
                    </p>
                    <div className="flex flex-wrap items-center gap-1.5 text-xs text-amber-800">
                      <span className="font-mono bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                        {purpose}
                      </span>
                      {scopes.map((s) => (
                        <span key={s} className="bg-white border border-amber-200 text-amber-700 px-1.5 py-0.5 rounded">
                          {s}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

              </div>
            </div>
          </div>

          <div className="px-6 py-4 border-t border-slate-200 flex gap-3 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="border border-slate-300 text-slate-700 text-sm font-medium px-4 py-2 rounded-lg hover:border-slate-400 hover:bg-slate-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !delegateId || scopes.length === 0}
              className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors"
            >
              {submitting ? "Creating…" : "Grant Delegation"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
