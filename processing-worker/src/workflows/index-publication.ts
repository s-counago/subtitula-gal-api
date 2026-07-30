import {
  WorkflowEntrypoint,
  type WorkflowEvent,
  type WorkflowStep,
} from "cloudflare:workers";
import { NonRetryableError } from "cloudflare:workflows";
import { InternalApi } from "../internal-api";
import { embedTexts } from "../schemas/embeddings";
import type {
  EmbeddingIndexContext,
  IndexWorkflowParams,
  ProcessingEnv,
} from "../types";

export class IndexPublicationWorkflow extends WorkflowEntrypoint<
  ProcessingEnv,
  IndexWorkflowParams
> {
  async run(
    event: Readonly<WorkflowEvent<IndexWorkflowParams>>,
    step: WorkflowStep,
  ): Promise<{ publicationId: string; embeddedDocumentCount: number }> {
    const api = new InternalApi(this.env);
    try {
      const contextReference = await step.do(
        "snapshot active public search documents",
        retryInternal,
        async () => {
          const context = await api.embeddingContext(
            event.payload.jobId,
            event.payload.publicationId,
          );
          if (
            context.projectId !== event.payload.projectId
            || context.documents.length === 0
            || context.documents.length > 20_000
          ) {
            throw new NonRetryableError("embedding_context_invalid");
          }
          const key = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
            + `/search-index/jobs/${context.jobId}/embedding-context.json`;
          await this.env.MEDIA.put(key, JSON.stringify(context), {
            httpMetadata: { contentType: "application/json" },
            customMetadata: {
              kind: "public-search-embedding-context",
              publicationId: context.publicationId,
              modelVersion: context.modelVersion,
            },
          });
          return {
            key,
            count: context.documents.length,
            inputCharacters: context.documents.reduce(
              (total, document) => total + document.text.length,
              0,
            ),
            jobVersion: context.jobVersion,
            projectVersion: context.projectVersion,
          };
        },
      );

      await step.do("mark embedding projection started", retryInternal, () =>
        api.startEmbedding(event.payload.jobId, event.payload.publicationId, {
          workflowInstanceId: event.instanceId,
          expectedJobVersion: contextReference.jobVersion,
          expectedProjectVersion: contextReference.projectVersion,
        }));

      const batchSize = boundedNumber(
        this.env.EMBEDDING_BATCH_SIZE,
        1,
        64,
      );
      const batchCount = Math.ceil(contextReference.count / batchSize);
      for (let index = 0; index < batchCount; index++) {
        await step.do(
          `embed and persist public search batch ${index}`,
          {
            retries: { limit: 5, delay: "5 seconds", backoff: "exponential" },
            timeout: "5 minutes",
          },
          async () => {
            const context = await readJson<EmbeddingIndexContext>(
              this.env.MEDIA,
              contextReference.key,
            );
            const documents = context.documents.slice(
              index * batchSize,
              (index + 1) * batchSize,
            );
            if (documents.length === 0) {
              throw new NonRetryableError("embedding_batch_invalid");
            }
            const vectors = await embedTexts(
              this.env,
              documents.map((document) => document.text),
              context.dimensions,
            );
            await api.ingestEmbeddings(
              event.payload.jobId,
              event.payload.publicationId,
              {
                workflowInstanceId: event.instanceId,
                modelVersion: context.modelVersion,
                dimensions: context.dimensions,
                items: documents.map((document, itemIndex) => ({
                  searchDocumentId: document.id,
                  contentHash: document.contentHash,
                  embedding: vectors[itemIndex],
                })),
              },
            );
            return { persisted: documents.length };
          },
        );
      }

      const estimatedInputTokens = Math.ceil(
        contextReference.inputCharacters
          / boundedNumber(this.env.ESTIMATED_CHARS_PER_TOKEN, 1, 20),
      );
      const unitRate = positiveInteger(
        this.env.EMBEDDING_INPUT_MICRO_USD_PER_MILLION_TOKENS,
      );
      const costMicrounits = Math.ceil(
        estimatedInputTokens * unitRate / 1_000_000,
      );
      await step.do("complete semantic search projection", retryInternal, () =>
        api.completeEmbeddings(
          event.payload.jobId,
          event.payload.publicationId,
          {
            workflowInstanceId: event.instanceId,
            modelVersion: this.env.EMBEDDING_MODEL_ID,
            providerUsage: {
              provider: "cloudflare-workers-ai",
              model: this.env.EMBEDDING_MODEL_ID,
              documentCount: contextReference.count,
              batchCount,
              inputCharacters: contextReference.inputCharacters,
              estimatedInputTokens,
              estimated: true,
              inputMicroUsdPerMillionTokens: unitRate,
              pricingVersion: "2026-07-29",
            },
            costMicrounits,
            costCurrency: "USD",
          },
        ));
      return {
        publicationId: event.payload.publicationId,
        embeddedDocumentCount: contextReference.count,
      };
    } catch (error) {
      await step.do("record safe embedding failure", async () => {
        try {
          await api.failEmbeddings(
            event.payload.jobId,
            event.payload.publicationId,
            {
              workflowInstanceId: event.instanceId,
              errorCode: "embedding_unavailable",
              safeMessage:
                "A busca por significado aínda non está lista. A busca por termos segue dispoñible.",
            },
          );
        } catch {
          // Workflow history remains available for reconciliation and retry.
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

async function readJson<T>(bucket: R2Bucket, key: string): Promise<T> {
  const object = await bucket.get(key);
  if (!object) throw new Error("embedding_context_missing");
  return object.json<T>();
}

function boundedNumber(value: string, minimum: number, maximum: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return minimum;
  return Math.max(minimum, Math.min(maximum, Math.floor(parsed)));
}

function positiveInteger(value: string): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0 || parsed > 1_000_000_000) {
    throw new NonRetryableError("embedding_pricing_invalid");
  }
  return parsed;
}
