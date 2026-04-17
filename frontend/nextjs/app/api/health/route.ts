import { NextResponse } from "next/server";

interface ServiceConfig {
  name: string;
  port: number;
  healthUrl: string;
  note?: string;
}

const SERVICES: ServiceConfig[] = [
  { name: "employee-bff",       port: 8085, healthUrl: "http://localhost:8085/actuator/health" },
  { name: "travel-service",     port: 8081, healthUrl: "http://localhost:8081/actuator/health" },
  { name: "expense-service",    port: 8082, healthUrl: "http://localhost:8082/actuator/health" },
  { name: "delegation-service", port: 8083, healthUrl: "http://localhost:8083/actuator/health" },
  { name: "consent-service",    port: 8084, healthUrl: "http://localhost:8084/actuator/health" },
  {
    name: "keycloak",
    port: 8080,
    // Keycloak 21+ exposes /health/live; /realms/... returns 200 when up
    healthUrl: "http://localhost:8080/realms/corporate-travel/.well-known/openid-configuration",
    note: "realm: corporate-travel",
  },
  { name: "opa", port: 8181, healthUrl: "http://localhost:8181/health" },
];

export interface ServiceHealth {
  name: string;
  port: number;
  status: "UP" | "DOWN";
  latencyMs: number;
  note?: string;
}

async function checkService(svc: ServiceConfig): Promise<ServiceHealth> {
  const start = Date.now();
  try {
    const res = await fetch(svc.healthUrl, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
    });
    const latencyMs = Date.now() - start;
    const isUp = res.ok;

    // Try to extract Spring Boot Actuator status from body
    let note = svc.note;
    try {
      const body = await res.json() as { status?: string };
      if (body.status && !svc.note) note = body.status;
    } catch {
      // Non-JSON body is fine (OPA, Keycloak)
    }

    return { name: svc.name, port: svc.port, status: isUp ? "UP" : "DOWN", latencyMs, note };
  } catch {
    return {
      name: svc.name,
      port: svc.port,
      status: "DOWN",
      latencyMs: Date.now() - start,
      note: "unreachable",
    };
  }
}

export async function GET() {
  const services = await Promise.all(SERVICES.map(checkService));
  return NextResponse.json({
    services,
    checkedAt: new Date().toISOString(),
  });
}
