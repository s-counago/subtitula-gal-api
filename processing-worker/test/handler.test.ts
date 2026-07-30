import { describe, expect, it, vi } from "vitest";
import worker, {
  cleanupExpiredUploads,
  cleanupSearchAnalytics,
} from "../src/index";
import type { ProcessingEnv } from "../src/types";

describe("processing HTTP boundary", () => {
  it("has a bounded health response", async () => {
    const response = await worker.fetch(
      new Request("http://processing.test/ping"),
      testEnv(),
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ status: "ok" });
  });

  it("rejects an unsigned provider artifact without storing it", async () => {
    const response = await worker.fetch(
      new Request(
        "http://processing.test/webhooks/elevenlabs/speech-to-text",
        {
          method: "POST",
          body: JSON.stringify({ type: "speech_to_text_transcription" }),
          headers: { "content-type": "application/json" },
        },
      ),
      testEnv(),
    );

    expect(response.status).toBe(401);
    await expect(response.json()).resolves.toEqual({
      error: "webhook_signature_invalid",
    });
  });

  it("rejects cross-site enrichment starts before touching a Workflow", async () => {
    const response = await worker.fetch(
      new Request(
        "http://processing.test/processing/projects/"
          + "11111111-1111-4111-8111-111111111111/enrichment",
        {
          method: "POST",
          body: JSON.stringify({
            jobId: "22222222-2222-4222-8222-222222222222",
            attemptId: "33333333-3333-4333-8333-333333333333",
          }),
          headers: { "content-type": "application/json" },
        },
      ),
      testEnv(),
    );

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({
      error: "origin_not_allowed",
    });
  });

  it("rejects cross-site semantic index starts before reading public data", async () => {
    const response = await worker.fetch(
      new Request(
        "http://processing.test/processing/publications/"
          + "11111111-1111-4111-8111-111111111111/index",
        {
          method: "POST",
          body: JSON.stringify({
            jobId: "22222222-2222-4222-8222-222222222222",
            attemptId: "33333333-3333-4333-8333-333333333333",
          }),
          headers: { "content-type": "application/json" },
        },
      ),
      testEnv(),
    );

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({
      error: "origin_not_allowed",
    });
  });

  it("rejects cross-site ingestion retries before changing job state", async () => {
    const response = await worker.fetch(
      new Request(
        "http://processing.test/processing/projects/"
          + "11111111-1111-4111-8111-111111111111/ingestion/retry",
        {
          method: "POST",
          body: JSON.stringify({
            jobId: "22222222-2222-4222-8222-222222222222",
            attemptId: "33333333-3333-4333-8333-333333333333",
          }),
          headers: { "content-type": "application/json" },
        },
      ),
      testEnv(),
    );

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({
      error: "origin_not_allowed",
    });
  });

  it("rate-limits semantic search before spending an AI invocation", async () => {
    const env = {
      ...testEnv(),
      SEARCH_RATE_LIMITER: {
        limit: async () => ({ success: false }),
      },
    } as ProcessingEnv;
    const response = await worker.fetch(
      new Request(
        "http://processing.test/processing/search?q=orzamento+da+auga",
        { headers: { "cf-connecting-ip": "203.0.113.20" } },
      ),
      env,
    );

    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBe("60");
    await expect(response.json()).resolves.toEqual({
      error: "rate_limited",
      message: "Too many requests. Try again later.",
    });
  });

  it("rate-limits browser processing mutations before external work", async () => {
    const env = {
      ...testEnv(),
      MUTATION_RATE_LIMITER: {
        limit: async () => ({ success: false }),
      },
    } as ProcessingEnv;
    const response = await worker.fetch(
      new Request("http://processing.test/processing/upload-intents", {
        method: "POST",
        body: "{}",
        headers: {
          "content-type": "application/json",
          origin: "http://localhost:3000",
          cookie: "XSRF-TOKEN=test-token",
          "x-xsrf-token": "test-token",
        },
      }),
      env,
    );

    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBe("60");
    await expect(response.json()).resolves.toEqual({
      error: "rate_limited",
    });
  });

  it("deletes bounded expired-upload candidates before finalizing them", async () => {
    const deleteObject = vi.fn(async () => undefined);
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path.endsWith("/candidates")) {
        const body = await request.json() as {
          expiredBefore: string;
          limit: number;
        };
        expect(body.limit).toBe(100);
        expect(body.expiredBefore).toBe("2026-07-29T23:00:00.000Z");
        return Response.json([{
          intentId: "11111111-1111-4111-8111-111111111111",
          projectId: "22222222-2222-4222-8222-222222222222",
          recordingId: "33333333-3333-4333-8333-333333333333",
          objectKey: "development/organizations/personal/projects/opaque/original",
          expiresAt: "2026-07-29T22:00:00Z",
          intentVersion: 2,
          recordingVersion: 3,
        }]);
      }
      expect(path).toContain(
        "/cleanup/upload-intents/11111111-1111-4111-8111-111111111111/complete",
      );
      return new Response(null, { status: 204 });
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      UPLOAD_CLEANUP_GRACE_SECONDS: "3600",
      API_SERVICE: { fetch: apiFetch },
      MEDIA: { delete: deleteObject },
    } as unknown as ProcessingEnv;

    await expect(cleanupExpiredUploads(
      env,
      Date.parse("2026-07-30T00:00:00Z"),
    )).resolves.toEqual({ selected: 1, deleted: 1 });
    expect(deleteObject).toHaveBeenCalledWith(
      "development/organizations/personal/projects/opaque/original",
    );
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });

  it("uses the configured privacy retention window for search analytics", async () => {
    const apiFetch = vi.fn(async (request: Request) => {
      const body = await request.json() as { createdBefore: string };
      expect(body.createdBefore).toBe("2026-05-01T00:00:00.000Z");
      return Response.json({
        createdBefore: body.createdBefore,
        deletedQueryEvents: 7,
      });
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      SEARCH_ANALYTICS_RETENTION_DAYS: "90",
      API_SERVICE: { fetch: apiFetch },
    } as unknown as ProcessingEnv;

    await expect(cleanupSearchAnalytics(
      env,
      Date.parse("2026-07-30T00:00:00Z"),
    )).resolves.toEqual({
      createdBefore: "2026-05-01T00:00:00.000Z",
      deletedQueryEvents: 7,
    });
  });
});

function testEnv(): ProcessingEnv {
  return {
    ALLOWED_ORIGIN: "http://localhost:3000",
    WEBHOOK_MAX_BYTES: "67108864",
    ELEVENLABS_WEBHOOK_SECRET: "webhook-secret",
  } as ProcessingEnv;
}
