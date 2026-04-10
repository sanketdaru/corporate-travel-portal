"use client";

import React, { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { bffClient, setAccessToken } from "@/lib/api/client";

export interface DelegationState {
  delegationActive: boolean;
  actorId: string | null;
  actorName: string | null;
  subjectId: string | null;
  subjectName: string | null;
  delegationId: string | null;
  consentId: string | null;
  purpose: string | null;
  expiresAt: string | null;
}

interface DelegationContextValue extends DelegationState {
  exitDelegation: () => Promise<void>;
  refreshContext: () => Promise<void>;
}

const defaultState: DelegationState = {
  delegationActive: false,
  actorId: null,
  actorName: null,
  subjectId: null,
  subjectName: null,
  delegationId: null,
  consentId: null,
  purpose: null,
  expiresAt: null,
};

const DelegationContext = createContext<DelegationContextValue>({
  ...defaultState,
  exitDelegation: async () => {},
  refreshContext: async () => {},
});

/** Converts a Keycloak username like "dave.assistant" → "Dave Assistant" */
function formatDisplayName(userId: string): string {
  return userId
    .split(".")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

interface BffContextResponse {
  delegationActive: boolean;
  delegationId?: string;
  actorId?: string;
  subjectId?: string;
  audience?: string;
  consentId?: string;
  purpose?: string;
  expiresAt?: string;
}

export function DelegationContextProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { data: session } = useSession();
  const [state, setState] = useState<DelegationState>(defaultState);

  const refreshContext = useCallback(async () => {
    try {
      const res = await bffClient.get<BffContextResponse>("/api/bff/delegation/context");
      const data = res.data;

      if (!data.delegationActive) {
        setState(defaultState);
        return;
      }

      setState({
        delegationActive: true,
        delegationId: data.delegationId ?? null,
        actorId: data.actorId ?? null,
        actorName: data.actorId ? formatDisplayName(data.actorId) : null,
        subjectId: data.subjectId ?? null,
        subjectName: data.subjectId ? formatDisplayName(data.subjectId) : null,
        consentId: data.consentId ?? null,
        purpose: data.purpose ?? null,
        expiresAt: data.expiresAt ?? null,
      });
    } catch {
      // If the BFF is unreachable or returns an error, treat as no active delegation
      setState(defaultState);
    }
  }, []);

  // Fetch delegation context once the session (and access token) is available.
  // Guard against the initial mount where the token hasn't been set yet.
  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    refreshContext();
  }, [session?.accessToken, refreshContext]);

  const exitDelegation = useCallback(async () => {
    try {
      await bffClient.delete("/api/bff/delegation/deactivate");
    } finally {
      setState(defaultState);
    }
  }, []);

  return (
    <DelegationContext.Provider value={{ ...state, exitDelegation, refreshContext }}>
      {children}
    </DelegationContext.Provider>
  );
}

export function useDelegationContext() {
  return useContext(DelegationContext);
}
