import {
  WorkflowEntrypoint,
  type WorkflowEvent,
  type WorkflowStep,
} from "cloudflare:workers";
import { NonRetryableError } from "cloudflare:workflows";
import { InternalApi } from "../internal-api";
import {
  ProviderError,
  submitTranscription,
  type ElevenLabsWebhook,
} from "../providers/elevenlabs";
import { presignProviderRead } from "../r2/presign";
import { normalizeTranscript } from "../schemas/normalize-transcript";
import type {
  IngestWorkflowParams,
  ProcessingEnv,
  ProviderArtifactEvent,
} from "../types";

export class IngestSessionWorkflow extends WorkflowEntrypoint<
  ProcessingEnv,
  IngestWorkflowParams
> {
  async run(
    event: Readonly<WorkflowEvent<IngestWorkflowParams>>,
    step: WorkflowStep,
  ): Promise<{ revisionId: string; segmentCount: number }> {
    const api = new InternalApi(this.env);
    try {
      const context = await step.do(
        "load verified ingest context",
        retryInternal,
        () => api.jobContext(event.payload.jobId),
      );
      if (
        context.projectId !== event.payload.projectId
        || context.recordingId !== event.payload.recordingId
        || context.objectKey !== event.payload.objectKey
      ) {
        throw new NonRetryableError("transcript_invalid");
      }

      const started = await step.do(
        "mark transcription started",
        retryInternal,
        () => api.startJob(event.payload.jobId, {
          expectedJobVersion: context.jobVersion,
          expectedProjectVersion: context.projectVersion,
        }),
      );
      const sourceUrl = await step.do(
        "issue provider media url",
        () => presignProviderRead(
          this.env,
          context.objectKey,
          Number(this.env.PROVIDER_SOURCE_TTL_SECONDS),
        ),
      );
      const submission = await step.do(
        "submit scribe transcription",
        {
          retries: { limit: 5, delay: "10 seconds", backoff: "exponential" },
          timeout: "2 minutes",
        },
        async () => {
          try {
            return await submitTranscription(this.env, sourceUrl, {
              jobId: event.payload.jobId,
              workflowInstanceId: event.instanceId,
            }, context.languageCode);
          } catch (error) {
            if (error instanceof ProviderError && !error.retryable) {
              throw new NonRetryableError(error.code);
            }
            throw error;
          }
        },
      );
      const waiting = await step.do(
        "persist provider correlation",
        retryInternal,
        () => api.providerSubmitted(event.payload.jobId, {
          providerRequestId: submission.requestId,
          expectedJobVersion: started.jobVersion,
        }),
      );

      let artifactEvent;
      try {
        artifactEvent = await step.waitForEvent<ProviderArtifactEvent>(
          "wait for scribe webhook",
          { type: "transcription_complete", timeout: "24 hours" },
        );
      } catch {
        throw new NonRetryableError("provider_timeout");
      }

      const normalizedReference = await step.do(
        "normalize provider artifact",
        { retries: { limit: 3, delay: "5 seconds", backoff: "exponential" } },
        async () => {
          const object = await this.env.MEDIA.get(artifactEvent.payload.artifactKey);
          if (!object) throw new Error("provider_artifact_missing");
          if (object.size > Number(this.env.WEBHOOK_MAX_BYTES)) {
            throw new NonRetryableError("provider_payload_invalid");
          }
          const webhook = await object.json<ElevenLabsWebhook>();
          if (
            webhook.data.request_id !== submission.requestId
            || webhook.data.webhook_metadata.jobId !== event.payload.jobId
          ) {
            throw new NonRetryableError("provider_payload_invalid");
          }
          const normalized = normalizeTranscript(webhook.data.transcription);
          const key = `${this.env.ENVIRONMENT}/projects/${event.payload.projectId}`
            + `/transcripts/normalized/${artifactEvent.payload.payloadDigest}.json`;
          await this.env.MEDIA.put(key, JSON.stringify(normalized), {
            httpMetadata: { contentType: "application/json" },
            customMetadata: {
              schemaVersion: normalized.schemaVersion,
              contentDigest: artifactEvent.payload.payloadDigest,
            },
          });
          return {
            key,
            languageCode: normalized.languageCode,
            durationMs: normalized.durationMs,
            speakerCount: normalized.speakers.length,
            segmentCount: normalized.segments.length,
          };
        },
      );

      const result = await step.do(
        "persist normalized transcript",
        retryInternal,
        async () => {
          const object = await this.env.MEDIA.get(normalizedReference.key);
          if (!object) throw new Error("normalized_artifact_missing");
          const normalized = await object.json<{
            languageCode: string;
            durationMs: number;
            speakers: Array<{ providerLabel: string }>;
            segments: unknown[];
          }>();
          const unitRate = positiveInteger(
            this.env.ELEVENLABS_SCRIBE_MICRO_USD_PER_HOUR,
          );
          const costMicrounits = Math.ceil(
            normalized.durationMs * unitRate / 3_600_000,
          );
          return api.ingestTranscript(event.payload.jobId, {
            provider: "elevenlabs",
            model: this.env.ELEVENLABS_MODEL_ID,
            languageCode: normalized.languageCode,
            keytermVersion: null,
            rawArtifactKey: artifactEvent.payload.artifactKey,
            contentHash: artifactEvent.payload.payloadDigest,
            durationMs: normalized.durationMs,
            providerUsage: {
              provider: "elevenlabs",
              model: this.env.ELEVENLABS_MODEL_ID,
              audioDurationMs: normalized.durationMs,
              pricingBasis: "configured_micro_usd_per_audio_hour",
              unitRate,
              pricingVersion: "2026-07-29",
            },
            costMicrounits,
            costCurrency: "USD",
            expectedJobVersion: waiting.jobVersion,
            expectedProjectVersion: waiting.projectVersion,
            speakers: normalized.speakers,
            segments: normalized.segments,
          });
        },
      );
      return {
        revisionId: result.revisionId,
        segmentCount: result.segmentCount,
      };
    } catch (error) {
      const safe = classifyFailure(error);
      await step.do("record safe ingest failure", async () => {
        try {
          const current = await api.jobContext(event.payload.jobId);
          if (!["succeeded", "failed_terminal", "cancelled"].includes(current.state)) {
            await api.failJob(event.payload.jobId, {
              errorCode: safe.code,
              safeMessage: safe.message,
              expectedJobVersion: current.jobVersion,
              expectedProjectVersion: current.projectVersion,
            });
          }
        } catch {
          // The Workflow remains errored and can be reconciled from its durable state.
        }
        return { recorded: true };
      });
      throw error;
    }
  }
}

const retryInternal = {
  retries: { limit: 8, delay: "5 seconds", backoff: "exponential" },
  timeout: "2 minutes",
} as const;

function positiveInteger(value: string): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0 || parsed > 1_000_000_000) {
    throw new NonRetryableError("provider_pricing_invalid");
  }
  return parsed;
}

function classifyFailure(error: unknown): { code: string; message: string } {
  const code = error instanceof ProviderError
    ? error.code
    : error instanceof Error && safeCodes.has(error.message)
      ? error.message
      : "unknown";
  return {
    code,
    message: failureCopy[code] ?? "O proceso detívose. Podes tentalo de novo.",
  };
}

const safeCodes = new Set([
  "provider_timeout",
  "provider_rejected",
  "provider_payload_invalid",
  "transcript_invalid",
]);

const failureCopy: Record<string, string> = {
  provider_unavailable: "O servizo de transcrición non está dispoñible agora.",
  provider_rate_limited: "O servizo está ocupado. Podes tentalo de novo máis tarde.",
  provider_timeout: "A transcrición tardou máis do esperado.",
  provider_rejected: "O servizo non aceptou este ficheiro.",
  provider_payload_invalid: "A resposta da transcrición non se puido validar.",
  transcript_invalid: "A transcrición non contén tramos válidos.",
};
