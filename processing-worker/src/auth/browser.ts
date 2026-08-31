import { constantTimeEqual } from "./crypto";

function cookie(request: Request, name: string): string | null {
  const value = request.headers.get("cookie");
  if (!value) return null;
  for (const part of value.split(";")) {
    const [candidate, ...rest] = part.trim().split("=");
    if (candidate === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

export function hasValidCsrfEcho(request: Request): boolean {
  const header = request.headers.get("x-xsrf-token");
  const token = cookie(request, "XSRF-TOKEN");
  if (!header || !token) return false;
  return constantTimeEqual(
    new TextEncoder().encode(header),
    new TextEncoder().encode(token),
  );
}
