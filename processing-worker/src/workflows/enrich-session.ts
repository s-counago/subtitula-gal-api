import {
  WorkflowEntrypoint,
  type WorkflowEvent,
  type WorkflowStep,
} from "cloudflare:workers";
import { NonRetryableError } from "cloudflare:workflows";
import { InternalApi } from "../internal-api";
import {
  alignAgenda,
  buildGuideWindows,
  type AgendaAlignmentDraft,
  type GuideWindow,
} from "../schemas/align-agenda";
import {
  generateGuideWindow,
  materializeGuide,
  type PersistedGuidePayload,
  type ValidatedWindow,
} from "../schemas/guide-extraction";
import type {
  EnrichmentContext,
  EnrichWorkflowParams,
  ProcessingEnv,
} from "../types";

type EnrichmentPlan = {
  alignments: AgendaAlignmentDraft[];
  windows: GuideWindow[];
};

export class EnrichSessionWorkflow extends WorkflowEntrypoint<
  ProcessingEnv,
  EnrichWorkflowParams
> {
  async run(
    event: Readonly<WorkflowEvent<EnrichWorkflowParams>>,
    step: WorkflowStep,
  ): Promise<{
    guideId: string;
    topicCount: number;
    candidateDecisionCount: number;
  }> {
    const api = new InternalApi(this.env);
    try {
      const contextReference = await step.do(
        "snapshot frozen enrichment context",
        retryInternal,
        async () => {
          const context = await api.enrichmentContext(event.payload.jobId);
          if (
            context.projectId !== event.payload.projectId
            || context.segments.length === 0
          ) {
            throw new NonRetryableError("guide_context_invalid");
          }
          const key = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
            + `/guides/jobs/${context.jobId}/context.json`;
          await this.env.MEDIA.put(key, JSON.stringify(context), {
            httpMetadata: { contentType: "application/json" },
            customMetadata: {
              kind: "frozen-enrichment-context",
              transcriptRevisionId: context.transcriptRevisionId,
            },
          });
          return {
            key,
            jobVersion: context.jobVersion,
            projectVersion: context.projectVersion,
          };
        },
      );

      const started = await step.do(
        "mark enrichment started",
        retryInternal,
        () => api.startEnrichment(event.payload.jobId, {
          workflowInstanceId: event.instanceId,
          expectedJobVersion: contextReference.jobVersion,
          expectedProjectVersion: contextReference.projectVersion,
        }),
      );

      const planReference = await step.do(
        "align agenda and plan bounded windows",
        retryStorage,
        async () => {
          const context = await readJson<EnrichmentContext>(
            this.env.MEDIA,
            contextReference.key,
          );
          const alignments = alignAgenda(context.agendaItems, context.segments);
          const windows = buildGuideWindows(
            context.segments,
            alignments,
            boundedNumber(this.env.GUIDE_MAX_WINDOW_CHARS, 8_000, 50_000),
            80,
          );
          if (windows.length === 0 || windows.length > 120) {
            throw new NonRetryableError("guide_window_count_invalid");
          }
          const plan: EnrichmentPlan = { alignments, windows };
          const key = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
            + `/guides/jobs/${context.jobId}/plan.json`;
          await this.env.MEDIA.put(key, JSON.stringify(plan), {
            httpMetadata: { contentType: "application/json" },
            customMetadata: {
              kind: "enrichment-plan",
              alignmentVersion: alignments[0]?.algorithmVersion ?? "none",
            },
          });
          return { key, windowCount: windows.length };
        },
      );

      const generatedReferences: string[] = [];
      for (let index = 0; index < planReference.windowCount; index++) {
        const reference = await step.do(
          `generate cited guide window ${index}`,
          {
            retries: { limit: 3, delay: "10 seconds", backoff: "exponential" },
            timeout: "5 minutes",
          },
          async () => {
            const [context, plan] = await Promise.all([
              readJson<EnrichmentContext>(this.env.MEDIA, contextReference.key),
              readJson<EnrichmentPlan>(this.env.MEDIA, planReference.key),
            ]);
            const window = plan.windows[index];
            if (!window) throw new NonRetryableError("guide_window_invalid");
            const generated = await generateGuideWindow(
              this.env,
              context,
              window,
            );
            const key = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
              + `/guides/jobs/${context.jobId}/windows/${window.id}.json`;
            await this.env.MEDIA.put(key, JSON.stringify(generated), {
              httpMetadata: { contentType: "application/json" },
              customMetadata: {
                kind: "private-generated-guide-window",
                promptVersion: this.env.GUIDE_PROMPT_VERSION,
              },
            });
            return { key };
          },
        );
        generatedReferences.push(reference.key);
      }

      const finalReference = await step.do(
        "validate and assemble evidence linked guide",
        retryStorage,
        async () => {
          const [context, plan, generatedArtifacts] = await Promise.all([
            readJson<EnrichmentContext>(this.env.MEDIA, contextReference.key),
            readJson<EnrichmentPlan>(this.env.MEDIA, planReference.key),
            Promise.all(generatedReferences.map(async (key) => {
              return readJson<{
                validated: ValidatedWindow;
                usage: {
                  inputTokens: number;
                  outputTokens: number;
                  estimated: boolean;
                };
              }>(
                this.env.MEDIA,
                key,
              );
            })),
          ]);
          const inputTokens = generatedArtifacts.reduce(
            (total, value) => total + value.usage.inputTokens,
            0,
          );
          const outputTokens = generatedArtifacts.reduce(
            (total, value) => total + value.usage.outputTokens,
            0,
          );
          const inputRate = positiveInteger(
            this.env.GUIDE_INPUT_MICRO_USD_PER_MILLION_TOKENS,
          );
          const outputRate = positiveInteger(
            this.env.GUIDE_OUTPUT_MICRO_USD_PER_MILLION_TOKENS,
          );
          const costMicrounits = Math.ceil(
            (inputTokens * inputRate + outputTokens * outputRate) / 1_000_000,
          );
          const providerUsage = {
            provider: "cloudflare-workers-ai" as const,
            model: this.env.GUIDE_MODEL_ID,
            windowCount: generatedArtifacts.length,
            inputTokens,
            outputTokens,
            estimated: generatedArtifacts.some((value) => value.usage.estimated),
            inputMicroUsdPerMillionTokens: inputRate,
            outputMicroUsdPerMillionTokens: outputRate,
            pricingVersion: "2026-07-29",
          };
          const payload = await materializeGuide(
            context,
            generatedArtifacts.map((value) => value.validated),
            {
            schemaVersion: this.env.GUIDE_SCHEMA_VERSION,
            model: this.env.GUIDE_MODEL_ID,
            promptVersion: this.env.GUIDE_PROMPT_VERSION,
            rawArtifactKey: "",
            jobVersion: started.jobVersion,
            projectVersion: started.projectVersion,
            alignments: plan.alignments,
            providerUsage,
            costMicrounits,
          });
          const key = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
            + `/guides/validated/${payload.contentHash}.json`;
          payload.rawArtifactKey = key;
          await this.env.MEDIA.put(key, JSON.stringify(payload), {
            httpMetadata: { contentType: "application/json" },
            customMetadata: {
              kind: "validated-session-guide",
              contentHash: payload.contentHash,
              schemaVersion: payload.schemaVersion,
            },
          });
          return {
            key,
            contentHash: payload.contentHash,
            topicCount: payload.topics.length,
          };
        },
      );

      const result = await step.do(
        "persist validated guide",
        retryInternal,
        async () => {
          const payload = await readJson<PersistedGuidePayload>(
            this.env.MEDIA,
            finalReference.key,
          );
          return api.ingestGuide(event.payload.jobId, payload);
        },
      );
      return {
        guideId: result.guideId,
        topicCount: result.topicCount,
        candidateDecisionCount: result.candidateDecisionCount,
      };
    } catch (error) {
      const failure = classifyFailure(error);
      await step.do("record safe enrichment failure", async () => {
        try {
          const current = await api.enrichmentContext(event.payload.jobId);
          await api.failJob(event.payload.jobId, {
            errorCode: failure.code,
            safeMessage: failure.message,
            expectedJobVersion: current.jobVersion,
            expectedProjectVersion: current.projectVersion,
          });
        } catch {
          // The durable Workflow state remains available for reconciliation.
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

const retryStorage = {
  retries: { limit: 5, delay: "3 seconds", backoff: "exponential" },
  timeout: "2 minutes",
} as const;

async function readJson<T>(bucket: R2Bucket, key: string): Promise<T> {
  const object = await bucket.get(key);
  if (!object) throw new Error("enrichment_artifact_missing");
  return object.json<T>();
}

function boundedNumber(value: string, minimum: number, maximum: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return minimum;
  return Math.max(minimum, Math.min(maximum, parsed));
}

function positiveInteger(value: string): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0 || parsed > 1_000_000_000) {
    throw new NonRetryableError("guide_pricing_invalid");
  }
  return parsed;
}

function classifyFailure(error: unknown): { code: string; message: string } {
  const code = error instanceof Error && safeCodes.has(error.message)
    ? error.message
    : "guide_generation_failed";
  return {
    code,
    message: messages[code]
      ?? "Non se puido preparar a guía. A transcrición revisada segue gardada.",
  };
}

const safeCodes = new Set([
  "guide_context_invalid",
  "guide_window_count_invalid",
  "guide_schema_invalid",
  "guide_evidence_invalid",
  "guide_empty",
]);

const messages: Record<string, string> = {
  guide_context_invalid: "A transcrición revisada non se puido preparar para a guía.",
  guide_window_count_invalid: "A sesión é demasiado longa para este xerador de guía.",
  guide_schema_invalid: "A guía xerada non superou a validación de estrutura.",
  guide_evidence_invalid: "A guía xerada contiña unha referencia non válida.",
  guide_empty: "Non se atoparon temas con evidencia suficiente para a guía.",
};
