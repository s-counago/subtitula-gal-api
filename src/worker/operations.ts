export const OPERATIONS_PATH = "/__ops/container";

export interface ContainerOperations {
  operationsStatus(): Promise<{ suspended: boolean; running: boolean }>;
  setSuspended(suspended: boolean): Promise<void>;
}

// This secret is separate from the processor-to-Spring signing key. It is never
// forwarded to the container or browser gateways. Only dev exposes this route.
export async function handleOperations(
  request: Request,
  secret: string | undefined,
  control: () => ContainerOperations,
): Promise<Response> {
  const authorization = request.headers.get("authorization") ?? "";
  if (!secret || secret.length < 32 || authorization.length > 512) {
    return reply({ error: "operations_unauthorized" }, 401);
  }
  const encoder = new TextEncoder();
  const supplied = await crypto.subtle.digest("SHA-256", encoder.encode(authorization));
  const expected = await crypto.subtle.digest("SHA-256", encoder.encode(`Bearer ${secret}`));
  if (!crypto.subtle.timingSafeEqual(supplied, expected)) {
    return reply({ error: "operations_unauthorized" }, 401);
  }
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === OPERATIONS_PATH) {
    return reply(await control().operationsStatus());
  }
  if (request.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
  if (url.pathname !== `${OPERATIONS_PATH}/suspend`
    && url.pathname !== `${OPERATIONS_PATH}/resume`) {
    return reply({ error: "not_found" }, 404);
  }
  const container = control();
  await container.setSuspended(url.pathname.endsWith("/suspend"));
  // SIGTERM delivery is not proof of shutdown. The client must poll running.
  return reply(await container.operationsStatus(), 202);
}

function reply(body: unknown, status = 200): Response {
  return Response.json(body, { status, headers: { "cache-control": "no-store" } });
}
