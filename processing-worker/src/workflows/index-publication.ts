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
    const batchSize = boundedNumber(
      this.env.EMBEDDING_BATCH_SIZE,
      1,
      64,
    );
    try {
      const lexical = await step.do(
        "build durable lexical search projection",
        retryInternal,
        () => api.buildLexicalIndex(
          event.payload.jobId,
          event.payload.publicationId,
          event.payload.projectId,
          event.instanceId,
        ),
      );
      if (lexical.projectId !== event.payload.projectId) {
        throw new NonRetryableError("embedding_context_invalid");
      }
      if (!lexical.semanticIndexRequired) {
        return {
          publicationId: event.payload.publicationId,
          embeddedDocumentCount: 0,
        };
      }
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
          const batchPrefix = `${this.env.ENVIRONMENT}/projects/${context.projectId}`
            + `/search-index/jobs/${context.jobId}/batches`;
          const batchCount = Math.ceil(context.documents.length / batchSize);
          for (let index = 0; index < batchCount; index++) {
            const documents = context.documents.slice(
              index * batchSize,
              (index + 1) * batchSize,
            );
            await this.env.MEDIA.put(
              `${batchPrefix}/${index}.json`,
              JSON.stringify({
                modelVersion: context.modelVersion,
                dimensions: context.dimensions,
                documents,
              } satisfies EmbeddingBatchArtifact),
              {
                httpMetadata: { contentType: "application/json" },
                customMetadata: {
                  kind: "public-search-embedding-batch",
                  publicationId: context.publicationId,
                  modelVersion: context.modelVersion,
                },
              },
            );
          }
          return {
            batchPrefix,
            batchCount,
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

      const batchCount = contextReference.batchCount;
      for (let index = 0; index < batchCount; index++) {
        await step.do(
          `embed and persist public search batch ${index}`,
          {
            retries: { limit: 5, delay: "5 seconds", backoff: "exponential" },
            timeout: "5 minutes",
          },
          async () => {
            const batch = await readJson<EmbeddingBatchArtifact>(
              this.env.MEDIA,
              `${contextReference.batchPrefix}/${index}.json`,
            );
            const documents = batch.documents;
            if (documents.length === 0) {
              throw new NonRetryableError("embedding_batch_invalid");
            }
            const vectors = await embedTexts(
              this.env,
              documents.map((document) => document.text),
              batch.dimensions,
            );
            await api.ingestEmbeddings(
              event.payload.jobId,
              event.payload.publicationId,
              {
                workflowInstanceId: event.instanceId,
                modelVersion: batch.modelVersion,
                dimensions: batch.dimensions,
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
          try {
            await api.failLexicalIndex(event.payload.jobId);
          } catch {
            // Workflow history remains available for reconciliation and retry.
          }
        }
        return { recorded: true };
      });
      throw error;
    }
  }
}

type EmbeddingBatchArtifact = Pick<
  EmbeddingIndexContext,
  "modelVersion" | "dimensions" | "documents"
>;

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
