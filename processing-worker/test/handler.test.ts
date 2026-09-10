import { describe, expect, it, vi } from "vitest";
import worker, {
  cleanupExpiredUploads,
  cleanupSearchAnalytics,
  reconcilePendingWorkflows,
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

  it("rejects another project's enrichment job before changing it", async () => {
    const projectId = "11111111-1111-4111-8111-111111111111";
    const jobId = "22222222-2222-4222-8222-222222222222";
    const createBatch = vi.fn();
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === `/projects/${projectId}`) {
        return Response.json({ id: projectId });
      }
      expect(request.method).toBe("GET");
      expect(path).toBe(`/internal/processing/jobs/${jobId}/enrichment-context`);
      return Response.json({ projectId: "44444444-4444-4444-8444-444444444444" });
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      API_SERVICE: { fetch: apiFetch },
      MUTATION_RATE_LIMITER: { limit: async () => ({ success: true }) },
      ENRICH_SESSION: { createBatch },
    } as unknown as ProcessingEnv;
    const response = await worker.fetch(new Request(
      `http://processing.test/processing/projects/${projectId}/enrichment`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          origin: "http://localhost:3000",
          cookie: "XSRF-TOKEN=test-token; SESSION=owner-session",
          "x-xsrf-token": "test-token",
        },
        body: JSON.stringify({
          jobId,
          attemptId: "33333333-3333-4333-8333-333333333333",
        }),
      },
    ), env);
    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toEqual({ error: "project_not_found" });
    expect(apiFetch).toHaveBeenCalledTimes(2);
    expect(createBatch).not.toHaveBeenCalled();
  });

  it.each(["development", "production"])("authorizes a reopened %s project through its binding and returns a short-lived private R2 URL", async (environment) => {
    const projectId = "11111111-1111-4111-8111-111111111111";
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === `/projects/${projectId}`) {
        expect(request.headers.get("cookie")).toBe("SESSION=owner-session");
        return Response.json({ id: projectId });
      }
      expect(path).toBe(`/internal/processing/projects/${projectId}/media`);
      expect(request.headers.get("x-subtitula-signature")).toBeTruthy();
      return Response.json({
        objectKey: "development/organizations/personal/projects/opaque/original",
        mimeType: "video/mp4",
        sizeBytes: 10_000,
        etag: "recording-etag",
      });
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: environment,
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      API_SERVICE: { fetch: apiFetch },
      R2_S3_ENDPOINT: "https://account.r2.cloudflarestorage.com",
      R2_S3_ACCESS_KEY_ID: "test-access-key",
      R2_S3_SECRET_ACCESS_KEY: "test-secret-key",
      R2_BUCKET_NAME: "subtitula-media-development",
      MEDIA_URL_TTL_SECONDS: "900",
    } as unknown as ProcessingEnv;

    const response = await worker.fetch(new Request(
      `http://processing.test/processing/projects/${projectId}/media-url`,
      { headers: { cookie: "SESSION=owner-session" } },
    ), env);

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    const body = await response.json() as { url: string; expiresAt: string };
    expect(body.url).toContain("account.r2.cloudflarestorage.com");
    expect(body.url).toContain("X-Amz-Expires=900");
    expect(new Date(body.expiresAt).getTime()).toBeGreaterThan(Date.now());
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });

  it("commits expiry before deleting and confirms the R2 deletion", async () => {
    const order: string[] = [];
    const deleteObject = vi.fn(async () => {
      order.push("delete");
    });
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path.endsWith("/cleanup/upload-intents/candidates")) {
        order.push("select");
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
      if (path.includes("/cleanup/upload-intents/") && path.endsWith("/complete")) {
        order.push("expire");
        return Response.json({
          recordingId: "33333333-3333-4333-8333-333333333333",
          objectKey: "development/organizations/personal/projects/opaque/original",
          recordingVersion: 4,
        });
      }
      if (path.endsWith("/cleanup/recordings/candidates")) {
        order.push("pending");
        expect(await request.json()).toEqual({ limit: 100 });
        return Response.json([{
          recordingId: "33333333-3333-4333-8333-333333333333",
          objectKey: "development/organizations/personal/projects/opaque/original",
          recordingVersion: 4,
        }]);
      }
      expect(path).toContain(
        "/cleanup/recordings/33333333-3333-4333-8333-333333333333/complete",
      );
      order.push("confirm");
      expect(await request.json()).toEqual({ expectedRecordingVersion: 4 });
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
    expect(order).toEqual(["select", "expire", "pending", "delete", "confirm"]);
    expect(apiFetch).toHaveBeenCalledTimes(4);
  });

  it("leaves an expired recording retryable when R2 deletion fails", async () => {
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path.endsWith("/cleanup/upload-intents/candidates")) {
        return Response.json([]);
      }
      if (path.endsWith("/cleanup/recordings/candidates")) {
        return Response.json([{
          recordingId: "33333333-3333-4333-8333-333333333333",
          objectKey: "development/private/original",
          recordingVersion: 4,
        }]);
      }
      throw new Error("deletion must not be confirmed");
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      UPLOAD_CLEANUP_GRACE_SECONDS: "3600",
      API_SERVICE: { fetch: apiFetch },
      MEDIA: { delete: vi.fn(async () => { throw new Error("r2 unavailable"); }) },
    } as unknown as ProcessingEnv;

    await expect(cleanupExpiredUploads(env)).rejects.toThrow(
      "recording_cleanup_failed:1",
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

  it("rejects an oversized webhook before buffering its body", async () => {
    const response = await worker.fetch(
      new Request(
        "http://processing.test/webhooks/elevenlabs/speech-to-text",
        {
          method: "POST",
          body: "{}",
          headers: { "content-length": "16777217" },
        },
      ),
      testEnv(),
    );

    expect(response.status).toBe(413);
    await expect(response.json()).resolves.toEqual({
      error: "webhook_too_large",
    });
  });

  it("starts a queued enrichment Workflow without a browser request", async () => {
    const createBatch = vi.fn(async () => [{}]);
    const apiFetch = vi.fn(async (request: Request) => {
      expect(new URL(request.url).pathname).toBe(
        "/internal/processing/jobs/pending-workflows",
      );
      expect(await request.json()).toEqual({ limit: 100 });
      return Response.json([{
        jobId: "11111111-1111-4111-8111-111111111111",
        projectId: "22222222-2222-4222-8222-222222222222",
        type: "enrich",
        workflowInstanceId: "enrich-11111111-1111-4111-8111-111111111111",
        publicationId: null,
      }]);
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      API_SERVICE: { fetch: apiFetch },
      ENRICH_SESSION: { createBatch },
    } as unknown as ProcessingEnv;

    await expect(reconcilePendingWorkflows(env)).resolves.toEqual({
      selected: 1,
      started: 1,
    });
    expect(createBatch).toHaveBeenCalledWith([{
      id: "enrich-11111111-1111-4111-8111-111111111111",
      params: {
        jobId: "11111111-1111-4111-8111-111111111111",
        projectId: "22222222-2222-4222-8222-222222222222",
      },
      retention: { successRetention: "30 days", errorRetention: "30 days" },
    }]);
  });

  it("prepares and starts queued lexical indexing without a browser request", async () => {
    const createBatch = vi.fn(async () => [{}]);
    const apiFetch = vi.fn(async (request: Request) => {
      const path = new URL(request.url).pathname;
      if (path.endsWith("/jobs/pending-workflows")) {
        return Response.json([{
          jobId: "11111111-1111-4111-8111-111111111111",
          projectId: "22222222-2222-4222-8222-222222222222",
          type: "index",
          workflowInstanceId: "index-publication-job-1",
          publicationId: "33333333-3333-4333-8333-333333333333",
        }]);
      }
      expect(path).toContain("/lexical-workflow");
      expect(await request.json()).toEqual({
        projectId: "22222222-2222-4222-8222-222222222222",
        workflowInstanceId: "index-publication-job-1",
      });
      return Response.json({
        projectId: "22222222-2222-4222-8222-222222222222",
        semanticIndexRequired: true,
      });
    });
    const env = {
      ...testEnv(),
      ENVIRONMENT: "development",
      API_ORIGIN: "https://api.test",
      INTERNAL_API_HMAC_SECRET: "test-internal-signature-secret",
      API_SERVICE: { fetch: apiFetch },
      INDEX_PUBLICATION: { createBatch },
    } as unknown as ProcessingEnv;

    await expect(reconcilePendingWorkflows(env)).resolves.toEqual({
      selected: 1,
      started: 1,
    });
    expect(createBatch).toHaveBeenCalledOnce();
  });
});

function testEnv(): ProcessingEnv {
  return {
    ALLOWED_ORIGIN: "http://localhost:3000",
    WEBHOOK_MAX_BYTES: "16777216",
    ELEVENLABS_WEBHOOK_SECRET: "webhook-secret",
  } as ProcessingEnv;
}
