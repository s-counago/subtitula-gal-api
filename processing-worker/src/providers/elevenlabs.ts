import {
  constantTimeEqual,
  fromBase64Url,
  hmacSha256,
} from "../auth/crypto";
import type { ProcessingEnv } from "../types";

export type ElevenLabsWord = {
  text?: unknown;
  start?: unknown;
  end?: unknown;
  type?: unknown;
  speaker_id?: unknown;
  logprob?: unknown;
};

export type ElevenLabsTranscription = {
  language_code?: unknown;
  text?: unknown;
  words?: unknown;
};

export type ElevenLabsWebhook = {
  type: string;
  data: {
    request_id: string;
    webhook_metadata: {
      jobId: string;
      workflowInstanceId: string;
    };
    transcription: ElevenLabsTranscription;
  };
};

export class ProviderError extends Error {
  constructor(
    readonly code:
      | "provider_unavailable"
      | "provider_rate_limited"
      | "provider_rejected"
      | "provider_payload_invalid",
    readonly retryable: boolean,
  ) {
    super(code);
    this.name = "ProviderError";
  }
}

export async function submitTranscription(
  env: ProcessingEnv,
  sourceUrl: string,
  metadata: { jobId: string; workflowInstanceId: string },
): Promise<{ requestId: string }> {
  const form = new FormData();
  form.set("model_id", env.ELEVENLABS_MODEL_ID);
  form.set("source_url", sourceUrl);
  form.set("language_code", env.ELEVENLABS_LANGUAGE_HINT);
  form.set("diarize", "true");
  form.set("timestamps_granularity", "word");
  form.set("tag_audio_events", "true");
  form.set("webhook", "true");
  form.set("webhook_metadata", JSON.stringify(metadata));
  if (env.ELEVENLABS_WEBHOOK_ID) {
    form.set("webhook_id", env.ELEVENLABS_WEBHOOK_ID);
  }

  let response: Response;
  try {
    response = await fetch(`${env.ELEVENLABS_API_BASE}/v1/speech-to-text`, {
      method: "POST",
      headers: { "xi-api-key": env.ELEVENLABS_API_KEY },
      body: form,
    });
  } catch {
    throw new ProviderError("provider_unavailable", true);
  }
  if (!response.ok) {
    if (response.status === 429) {
      throw new ProviderError("provider_rate_limited", true);
    }
    if (response.status >= 500 || response.status === 408) {
      throw new ProviderError("provider_unavailable", true);
    }
    throw new ProviderError("provider_rejected", false);
  }
  const value = await response.json<unknown>();
  if (
    typeof value !== "object"
    || value === null
    || !("request_id" in value)
    || typeof value.request_id !== "string"
    || !value.request_id
  ) {
    throw new ProviderError("provider_payload_invalid", true);
  }
  return { requestId: value.request_id };
}

export async function verifyWebhook(
  rawBody: ArrayBuffer,
  signatureHeader: string | null,
  secret: string,
  nowEpochSec = Math.floor(Date.now() / 1000),
): Promise<boolean> {
  if (!signatureHeader || !secret) return false;
  let timestamp: number | null = null;
  const signatures: Uint8Array[] = [];
  for (const part of signatureHeader.split(",")) {
    const [key, value] = part.trim().split("=", 2);
    if (key === "t" && value && /^\d+$/.test(value)) timestamp = Number(value);
    if (key === "v0" && value && /^[a-fA-F0-9]{64}$/.test(value)) {
      signatures.push(Uint8Array.from(value.match(/.{2}/g) ?? [], (byte) => Number.parseInt(byte, 16)));
    } else if (key === "v0" && value) {
      try {
        signatures.push(fromBase64Url(value));
      } catch {
        // Ignore malformed candidate signatures.
      }
    }
  }
  if (timestamp === null || Math.abs(nowEpochSec - timestamp) > 300 || signatures.length === 0) {
    return false;
  }
  const prefix = new TextEncoder().encode(`${timestamp}.`);
  const message = new Uint8Array(prefix.byteLength + rawBody.byteLength);
  message.set(prefix, 0);
  message.set(new Uint8Array(rawBody), prefix.byteLength);
  const expected = await hmacSha256(secret, message);
  return signatures.some((candidate) => constantTimeEqual(expected, candidate));
}

export function parseWebhook(rawBody: ArrayBuffer): ElevenLabsWebhook {
  let root: unknown;
  try {
    root = JSON.parse(new TextDecoder().decode(rawBody));
  } catch {
    throw new ProviderError("provider_payload_invalid", false);
  }
  if (typeof root !== "object" || root === null) {
    throw new ProviderError("provider_payload_invalid", false);
  }
  const candidate = root as Record<string, unknown>;
  const data = candidate.data;
  if (typeof candidate.type !== "string" || typeof data !== "object" || data === null) {
    throw new ProviderError("provider_payload_invalid", false);
  }
  const record = data as Record<string, unknown>;
  const metadata = parseMetadata(record.webhook_metadata);
  if (
    typeof record.request_id !== "string"
    || !metadata
    || typeof record.transcription !== "object"
    || record.transcription === null
  ) {
    throw new ProviderError("provider_payload_invalid", false);
  }
  return {
    type: candidate.type,
    data: {
      request_id: record.request_id,
      webhook_metadata: metadata,
      transcription: record.transcription as ElevenLabsTranscription,
    },
  };
}

function parseMetadata(
  value: unknown,
): { jobId: string; workflowInstanceId: string } | null {
  let candidate = value;
  if (typeof candidate === "string") {
    try {
      candidate = JSON.parse(candidate);
    } catch {
      return null;
    }
  }
  if (typeof candidate !== "object" || candidate === null) return null;
  const record = candidate as Record<string, unknown>;
  return typeof record.jobId === "string"
    && typeof record.workflowInstanceId === "string"
    ? { jobId: record.jobId, workflowInstanceId: record.workflowInstanceId }
    : null;
}
