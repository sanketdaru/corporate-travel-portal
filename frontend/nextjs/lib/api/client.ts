"use client";

import axios from "axios";
import { signOut } from "next-auth/react";

// Module-level token store. Set by AppShell once session is available.
let _accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  _accessToken = token;
}

// ── BFF client (proxied through Next.js → port 8085) ─────────────────────────
// Requests go to the same Next.js origin; next.config.ts rewrites /api/bff/*
// to localhost:8085. This avoids CORS and keeps Spring session cookies working.

export const bffClient = axios.create({
  baseURL: "",
  withCredentials: true, // sends Spring session cookie (JSESSIONID)
});

bffClient.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

bffClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      signOut({ callbackUrl: "/login" });
    }
    return Promise.reject(err);
  }
);

// ── Gateway client (proxied through Next.js → port 8000) ──────────────────────
// next.config.ts rewrites /api/* (non-bff) to localhost:8000.

export const gatewayClient = axios.create({
  baseURL: "",
  withCredentials: true,
});

gatewayClient.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

gatewayClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      signOut({ callbackUrl: "/login" });
    }
    return Promise.reject(err);
  }
);
