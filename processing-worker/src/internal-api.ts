import { internalSignatureHeaders } from "./auth/internal-signature";
import type {
  EnrichmentContext,
  EmbeddingIndexContext,
  ExpiredUploadCandidate,
  InternalUploadIntent,
  JobContext,
  ProcessingEnv,
  RecordingDeletionCandidate,
  PendingWorkflow,
} from "./types";

export class InternalApiError extends Error {
  constructor(
    readonly status: number,
    readonly retryable: boolean,
    message: string,
  ) {
    super(message);
    this.name = "InternalApiError";
  }
}

export class InternalApi {
  constructor(private readonly env: ProcessingEnv) {}

  async assertProjectAccess(request: Request, projectId: string): Promise<void> {
    const path = `/projects/${encodeURIComponent(projectId)}`;
    const headers = new Headers();
    const cookie = request.headers.get("cookie");
    if (cookie) headers.set("cookie", cookie);
    headers.set("accept", "application/json");
    const response = await this.fetchApi(path, {
      method: "GET",
      headers,
      redirect: "manual",
    });
    if (!response.ok) {
      throw new InternalApiError(
        response.status,
        response.status >= 500,
        response.status === 404 ? "project_not_found" : "project_access_denied",
      );
    }
  }

  createUploadIntent(command: unknown): Promise<InternalUploadIntent> {
    return this.command("/internal/processing/upload-intents", "POST", command);
  }

  completeUpload(
    intentId: string,
    command: unknown,
  ): Promise<InternalUploadIntent> {
    return this.command(
      `/internal/processing/upload-intents/${encodeURIComponent(intentId)}/complete`,
      "POST",
      command,
    );
  }

  abortUpload(intentId: string): Promise<RecordingDeletionCandidate> {
    return this.command(
      `/internal/processing/upload-intents/${encodeURIComponent(intentId)}/abort`,
      "POST",
      {},
    );
  }

  expiredUploadCandidates(command: unknown): Promise<ExpiredUploadCandidate[]> {
    return this.command(
      "/internal/processing/cleanup/upload-intents/candidates",
      "POST",
      command,
    );
  }

  finalizeExpiredUpload(
    intentId: string,
    command: unknown,
  ): Promise<RecordingDeletionCandidate | undefined> {
    return this.command(
      `/internal/processing/cleanup/upload-intents/${
        encodeURIComponent(intentId)
      }/complete`,
      "POST",
      command,
    );
  }

  pendingRecordingDeletions(
    command: unknown,
  ): Promise<RecordingDeletionCandidate[]> {
    return this.command(
      "/internal/processing/cleanup/recordings/candidates",
      "POST",
      command,
    );
  }

  async confirmRecordingDeletion(
    recordingId: string,
    command: unknown,
  ): Promise<void> {
    await this.command(
      `/internal/processing/cleanup/recordings/${encodeURIComponent(recordingId)}/complete`,
      "POST",
      command,
    );
  }

  privateMedia(projectId: string): Promise<{
    objectKey: string;
    mimeType: string;
    sizeBytes: number;
    etag: string | null;
  }> {
    return this.command(
      `/internal/processing/projects/${encodeURIComponent(projectId)}/media`,
      "GET",
      undefined,
    );
  }

  pendingWorkflows(command: unknown): Promise<PendingWorkflow[]> {
    return this.command(
      "/internal/processing/jobs/pending-workflows",
      "POST",
      command,
    );
  }

  cleanupSearchAnalytics(command: unknown): Promise<{
    createdBefore: string;
    deletedQueryEvents: number;
  }> {
    return this.command(
      "/internal/processing/cleanup/search-analytics",
      "POST",
      command,
    );
  }

  jobContext(jobId: string): Promise<JobContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/context`,
      "GET",
      undefined,
    );
  }

  startJob(jobId: string, command: unknown): Promise<JobContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/start`,
      "POST",
      command,
    );
  }

  retryIngest(jobId: string, command: unknown): Promise<JobContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/ingest-retry`,
      "POST",
      command,
    );
  }

  enrichmentContext(jobId: string): Promise<EnrichmentContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/enrichment-context`,
      "GET",
      undefined,
    );
  }

  startEnrichment(
    jobId: string,
    command: unknown,
  ): Promise<{
    jobId: string;
    projectId: string;
    state: string;
    stage: string;
    jobVersion: number;
    projectVersion: number;
  }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/enrichment-start`,
      "POST",
      command,
    );
  }

  providerSubmitted(jobId: string, command: unknown): Promise<JobContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/provider-submitted`,
      "POST",
      command,
    );
  }

  webhookReceived(
    jobId: string,
    command: unknown,
  ): Promise<{ accepted: boolean; duplicate: boolean; workflowInstanceId: string }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/webhook-received`,
      "POST",
      command,
    );
  }

  ingestTranscript(
    jobId: string,
    command: unknown,
  ): Promise<{ revisionId: string; segmentCount: number; requiredIssueCount: number; duplicate: boolean }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/transcript`,
      "POST",
      command,
    );
  }

  ingestGuide(
    jobId: string,
    command: unknown,
  ): Promise<{
    guideId: string;
    topicCount: number;
    contributionCount: number;
    candidateDecisionCount: number;
    optionalAgendaCheckCount: number;
    duplicate: boolean;
  }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/guide`,
      "POST",
      command,
    );
  }

  ingestAgenda(
    jobId: string,
    command: unknown,
  ): Promise<{
    alignmentCount: number;
    optionalCheckCount: number;
    duplicate: boolean;
  }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/agenda`,
      "POST",
      command,
    );
  }

  publicationMedia(
    slug: string,
    version: number,
  ): Promise<{
    objectKey: string;
    mimeType: string;
    sizeBytes: number;
    etag: string | null;
  }> {
    return this.command(
      `/internal/processing/publications/${encodeURIComponent(slug)}`
        + `/versions/${version}/media`,
      "GET",
      undefined,
    );
  }

  embeddingContext(
    jobId: string,
    publicationId: string,
  ): Promise<EmbeddingIndexContext> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/embedding-context`,
      "GET",
      undefined,
    );
  }

  prepareLexicalWorkflow(
    jobId: string,
    publicationId: string,
    projectId: string,
    workflowInstanceId: string,
  ): Promise<{ projectId: string; semanticIndexRequired: boolean }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/lexical-workflow`,
      "POST",
      { projectId, workflowInstanceId },
    );
  }

  buildLexicalIndex(
    jobId: string,
    publicationId: string,
    projectId: string,
    workflowInstanceId: string,
  ): Promise<{ projectId: string; semanticIndexRequired: boolean }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/lexical-index`,
      "POST",
      { projectId, workflowInstanceId },
    );
  }

  async failLexicalIndex(jobId: string): Promise<void> {
    await this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/lexical-index-failed`,
      "POST",
      {},
    );
  }

  startEmbedding(
    jobId: string,
    publicationId: string,
    command: unknown,
  ): Promise<{
    jobId: string;
    projectId: string;
    publicationId: string;
    state: string;
    stage: string;
    jobVersion: number;
    projectVersion: number;
  }> {
    return this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/embedding-start`,
      "POST",
      command,
    );
  }

  async ingestEmbeddings(
    jobId: string,
    publicationId: string,
    command: unknown,
  ): Promise<void> {
    await this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/embeddings`,
      "POST",
      command,
    );
  }

  async completeEmbeddings(
    jobId: string,
    publicationId: string,
    command: unknown,
  ): Promise<void> {
    await this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/embedding-complete`,
      "POST",
      command,
    );
  }

  async failEmbeddings(
    jobId: string,
    publicationId: string,
    command: unknown,
  ): Promise<void> {
    await this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}`
        + `/publications/${encodeURIComponent(publicationId)}/embedding-failed`,
      "POST",
      command,
    );
  }

  hybridSearch(command: unknown): Promise<unknown> {
    return this.command(
      "/internal/processing/search/hybrid",
      "POST",
      command,
    );
  }

  publicApi(path: string): Promise<Response> {
    return this.fetchApi(path, {
      method: "GET",
      headers: { accept: "application/json" },
      redirect: "manual",
    });
  }

  async failJob(jobId: string, command: unknown): Promise<void> {
    await this.command(
      `/internal/processing/jobs/${encodeURIComponent(jobId)}/failed`,
      "POST",
      command,
    );
  }

  private async command<T>(
    path: string,
    method: "GET" | "POST",
    value: unknown,
  ): Promise<T> {
    const body = method === "GET" ? "" : JSON.stringify(value ?? {});
    const headers = await internalSignatureHeaders(
      this.env.INTERNAL_API_HMAC_SECRET,
      method,
      path,
      body,
    );
    const response = await this.fetchApi(path, {
      method,
      headers,
      body: method === "GET" ? undefined : body,
      redirect: "manual",
    });
    if (!response.ok) {
      throw new InternalApiError(
        response.status,
        response.status >= 500 || response.status === 429,
        `internal_api_${response.status}`,
      );
    }
    if (response.status === 204) return undefined as T;
    return response.json<T>();
  }

  private fetchApi(path: string, init: RequestInit): Promise<Response> {
    const target = new URL(path, this.env.API_ORIGIN);
    const request = new Request(target, init);
    request.headers.delete("host");
    if (this.env.ENVIRONMENT === "development") {
      return this.env.API_SERVICE.fetch(request);
    }
    return fetch(request);
  }
}
