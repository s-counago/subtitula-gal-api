import { afterEach, describe, expect, it, vi } from "vitest";
import { submitTranscription } from "../src/providers/elevenlabs";
import type { ProcessingEnv } from "../src/types";

afterEach(() => vi.unstubAllGlobals());

describe("Scribe project language", () => {
  it.each([["spa", "spa"], ["glg", "glg"], [null, "glg"], ["", "glg"]])(
    "submits project language %s with Galician default %s",
    async (languageCode, expected) => {
      const fetcher = vi.fn(async (_url: string, init: RequestInit) => {
        const form = init.body as FormData;
        expect(form.get("language_code")).toBe(expected);
        expect(form.get("webhook_metadata")).toBe(JSON.stringify({ jobId: "job", workflowInstanceId: "workflow" }));
        return Response.json({ request_id: "request" });
      });
      vi.stubGlobal("fetch", fetcher);
      const env = {
        ELEVENLABS_MODEL_ID: "scribe_v2", ELEVENLABS_LANGUAGE_HINT: "glg",
        ELEVENLABS_API_BASE: "https://provider.test", ELEVENLABS_API_KEY: "synthetic",
      } as ProcessingEnv;
      await expect(submitTranscription(env, "https://media.test/synthetic", {
        jobId: "job", workflowInstanceId: "workflow",
      }, languageCode)).resolves.toEqual({ requestId: "request" });
      expect(fetcher).toHaveBeenCalledOnce();
    },
  );

  it("rejects other languages before contacting the provider", async () => {
    const fetcher = vi.fn();
    vi.stubGlobal("fetch", fetcher);
    for (const language of ["eng", "fra", "auto"]) {
      await expect(submitTranscription({} as ProcessingEnv, "https://media.test/synthetic", {
        jobId: "job", workflowInstanceId: "workflow",
      }, language)).rejects.toMatchObject({ code: "provider_rejected", retryable: false });
    }
    expect(fetcher).not.toHaveBeenCalled();
  });
});
