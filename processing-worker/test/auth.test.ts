import { describe, expect, it } from "vitest";
import { internalSignatureHeaders } from "../src/auth/internal-signature";
import {
  createUploadToken,
  verifyUploadToken,
  type UploadTokenPayload,
} from "../src/auth/upload-token";
import { verifyWebhook } from "../src/providers/elevenlabs";

describe("internal command authentication", () => {
  it("binds the exact method, path, timestamp, nonce and body digest", async () => {
    const headers = await internalSignatureHeaders(
      "test-secret",
      "POST",
      "/internal/processing/jobs/123/start",
      "{\"hello\":\"world\"}",
      new Date("2025-01-01T00:00:00Z"),
      "11111111-1111-4111-8111-111111111111",
    );

    expect(headers.get("x-subtitula-timestamp")).toBe("1735689600");
    expect(headers.get("x-subtitula-nonce")).toBe(
      "11111111-1111-4111-8111-111111111111",
    );
    expect(headers.get("x-subtitula-content-sha256")).toBe(
      "93a23971a914e5eacbf0a8d25154cda309c3c1c72fbb9914d47c60f3cb681588",
    );
    expect(headers.get("x-subtitula-signature")).toBe(
      "d8ed5dd9a6d61a5267686aad8c4effb52a775bb52b2d4dec3458a2b7f7187203",
    );
  });
});

describe("upload capability tokens", () => {
  const payload: UploadTokenPayload = {
    intentId: "11111111-1111-4111-8111-111111111111",
    projectId: "22222222-2222-4222-8222-222222222222",
    objectKey: "test/projects/222/recordings/111/original",
    sizeBytes: 42,
    mimeType: "video/mp4",
    expiresAtEpochSec: 2_000,
  };

  it("accepts only an untampered, unexpired token", async () => {
    const token = await createUploadToken("token-secret", payload);

    await expect(verifyUploadToken("token-secret", token, 1_999))
      .resolves.toEqual(payload);
    await expect(verifyUploadToken("wrong-secret", token, 1_999))
      .resolves.toBeNull();
    await expect(verifyUploadToken("token-secret", `${token}x`, 1_999))
      .resolves.toBeNull();
    await expect(verifyUploadToken("token-secret", token, 2_001))
      .resolves.toBeNull();
  });
});

describe("ElevenLabs webhook authentication", () => {
  const timestamp = 1_735_689_600;
  const raw = new TextEncoder().encode(
    "{\"type\":\"speech_to_text_transcription\","
      + "\"data\":{\"request_id\":\"req-1\"}}",
  ).buffer as ArrayBuffer;
  const signature =
    "505224cde24e6a5e63da7712972a99160af3f12bea9f7d0543c85ca5fb19f6ce";

  it("checks the signature against the untouched request bytes", async () => {
    await expect(verifyWebhook(
      raw,
      `t=${timestamp},v0=${signature}`,
      "webhook-secret",
      timestamp,
    )).resolves.toBe(true);

    const tampered = new TextEncoder().encode(
      "{\"type\":\"speech_to_text_transcription\","
        + "\"data\":{\"request_id\":\"req-2\"}}",
    ).buffer as ArrayBuffer;
    await expect(verifyWebhook(
      tampered,
      `t=${timestamp},v0=${signature}`,
      "webhook-secret",
      timestamp,
    )).resolves.toBe(false);
    await expect(verifyWebhook(
      raw,
      `t=${timestamp},v0=${signature}`,
      "webhook-secret",
      timestamp + 301,
    )).resolves.toBe(false);
  });
});
