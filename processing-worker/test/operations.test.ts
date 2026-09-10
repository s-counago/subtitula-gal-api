import { describe, expect, it, vi } from "vitest";
import { handleOperations, OPERATIONS_PATH } from "../../src/worker/operations";

const token = "synthetic-operations-token-for-tests-only";

describe("development container operations", () => {
  it("rejects absent, missing-config and incorrect credentials before resolving a container", async () => {
    const control = vi.fn();
    for (const [secret, auth] of [[undefined, `Bearer ${token}`], [token, ""], [token, "Bearer wrong"]]) {
      const response = await handleOperations(new Request(`https://api.test${OPERATIONS_PATH}`, {
        headers: { authorization: auth ?? "" },
      }), secret, control);
      expect(response.status).toBe(401);
    }
    expect(control).not.toHaveBeenCalled();
  });

  it("reads status without waking or stopping the instance", async () => {
    const control = {
      operationsStatus: vi.fn(async () => ({ suspended: true, running: false })),
      setSuspended: vi.fn(),
    };
    const response = await handleOperations(new Request(`https://api.test${OPERATIONS_PATH}`, {
      headers: { authorization: `Bearer ${token}` },
    }), token, () => control);
    expect(await response.json()).toEqual({ suspended: true, running: false });
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(control.setSuspended).not.toHaveBeenCalled();
  });

  it("awaits suspension but reports a still-running instance truthfully", async () => {
    const order: string[] = [];
    const response = await handleOperations(new Request(`https://api.test${OPERATIONS_PATH}/suspend`, {
      method: "POST", headers: { authorization: `Bearer ${token}` },
    }), token, () => ({
      setSuspended: async (suspended) => { expect(suspended).toBe(true); order.push("suspend"); },
      operationsStatus: async () => { order.push("status"); return { suspended: true, running: true }; },
    }));
    expect(order).toEqual(["suspend", "status"]);
    expect(response.status).toBe(202);
    expect(await response.json()).toEqual({ suspended: true, running: true });
  });

  it("resumes explicitly and rejects arbitrary command paths", async () => {
    const control = {
      operationsStatus: vi.fn(async () => ({ suspended: false, running: false })),
      setSuspended: vi.fn(async (_suspended: boolean) => {}),
    };
    for (const [suffix, status] of [["/resume", 202], ["/destroy", 404]] as const) {
      const response = await handleOperations(new Request(`https://api.test${OPERATIONS_PATH}${suffix}`, {
        method: "POST", headers: { authorization: `Bearer ${token}` },
      }), token, () => control);
      expect(response.status).toBe(status);
    }
    expect(control.setSuspended).toHaveBeenCalledExactlyOnceWith(false);
  });
});
