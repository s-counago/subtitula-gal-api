import { hasValidCsrfEcho } from "./auth/browser";
import { sha256 } from "./auth/crypto";
import {
  createUploadToken,
  verifyUploadToken,
} from "./auth/upload-token";
import { InternalApi, InternalApiError } from "./internal-api";
import {
  parseWebhook,
  ProviderError,
  verifyWebhook,
} from "./providers/elevenlabs";
import {
  canPresign,
  presignProviderRead,
  presignUpload,
} from "./r2/presign";
import type { ProcessingEnv } from "./types";
import { IngestSessionWorkflow } from "./workflows/ingest-session";
import { EnrichSessionWorkflow } from "./workflows/enrich-session";
import { IndexPublicationWorkflow } from "./workflows/index-publication";
import { embedTexts } from "./schemas/embeddings";

export {
  EnrichSessionWorkflow,
  IndexPublicationWorkflow,
  IngestSessionWorkflow,
};

type CreateIntentRequest = {
  projectId: string;
  clientRequestId: string;
  filename: string;
  sizeBytes: number;
  mimeType: string;
  checksumSha256?: string | null;
  usagePermission: boolean;
};

type CompleteIntentRequest = {
  projectId: string;
  uploadToken: string;
  checksumSha256?: string | null;
};

type StartEnrichmentRequest = {
  jobId: string;
  attemptId: string;
};

type StartIndexRequest = {
  jobId: string;
  attemptId: string;
  projectId: string;
};

type RetryIngestionRequest = {
  jobId: string;
  attemptId: string;
};

const workflowRetention = {
  successRetention: "30 days",
  errorRetention: "30 days",
} as const;

export default {
  async fetch(request: Request, env: ProcessingEnv): Promise<Response> {
    if (request.method === "OPTIONS") return preflight(request, env);
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/ping") {
        return json({ status: "ok" }, 200, request, env);
      }
      if (request.method === "POST" && url.pathname === "/processing/upload-intents") {
        return await createIntent(request, env);
      }
      const localUpload = url.pathname.match(/^\/processing\/uploads\/([0-9a-f-]+)$/);
      if (request.method === "PUT" && localUpload) {
        return await uploadLocally(request, env, localUpload[1]);
      }
      const complete = url.pathname.match(
        /^\/processing\/upload-intents\/([0-9a-f-]+)\/complete$/,
      );
      if (request.method === "POST" && complete) {
        return await completeIntent(request, env, complete[1]);
      }
      const abort = url.pathname.match(
        /^\/processing\/upload-intents\/([0-9a-f-]+)\/abort$/,
      );
      if (request.method === "POST" && abort) {
        return await abortIntent(request, env, abort[1]);
      }
      const enrichment = url.pathname.match(
        /^\/processing\/projects\/([0-9a-f-]+)\/enrichment$/,
      );
      if (request.method === "POST" && enrichment) {
        return await startEnrichment(request, env, enrichment[1]);
      }
      const privateMediaUrl = url.pathname.match(
        /^\/processing\/projects\/([0-9a-f-]+)\/media-url$/,
      );
      if (request.method === "GET" && privateMediaUrl) {
        return await privateRecordingUrl(request, env, privateMediaUrl[1]);
      }
      const privateMedia = url.pathname.match(
        /^\/processing\/projects\/([0-9a-f-]+)\/media$/,
      );
      if ((request.method === "GET" || request.method === "HEAD") && privateMedia) {
        return await privateRecordingMedia(request, env, privateMedia[1]);
      }
      const ingestionRetry = url.pathname.match(
        /^\/processing\/projects\/([0-9a-f-]+)\/ingestion\/retry$/,
      );
      if (request.method === "POST" && ingestionRetry) {
        return await retryIngestion(request, env, ingestionRetry[1]);
      }
      const indexPublication = url.pathname.match(
        /^\/processing\/publications\/([0-9a-f-]+)\/index$/,
      );
      if (request.method === "POST" && indexPublication) {
        return await startPublicationIndex(
          request,
          env,
          indexPublication[1],
        );
      }
      if (request.method === "GET" && url.pathname === "/processing/search") {
        return await hybridSearch(request, env, null);
      }
      const sessionSearch = url.pathname.match(
        /^\/processing\/sessions\/([a-z0-9]+(?:-[a-z0-9]+)*)\/search$/,
      );
      if (request.method === "GET" && sessionSearch) {
        return await hybridSearch(request, env, sessionSearch[1]);
      }
      const publicMedia = url.pathname.match(
        /^\/processing\/publications\/([a-z0-9]+(?:-[a-z0-9]+)*)\/media$/,
      );
      if ((request.method === "GET" || request.method === "HEAD") && publicMedia) {
        return await publicationMedia(request, env, publicMedia[1]);
      }
      if (
        request.method === "POST"
        && url.pathname === "/webhooks/elevenlabs/speech-to-text"
      ) {
        return await receiveElevenLabsWebhook(request, env);
      }
      return json({ error: "not_found" }, 404, request, env);
    } catch (error) {
      return safeError(error, request, env);
    }
  },
  async scheduled(
    controller: ScheduledController,
    env: ProcessingEnv,
  ): Promise<void> {
    const tasks: Promise<unknown>[] = [
      cleanupExpiredUploads(env, controller.scheduledTime),
      reconcilePendingWorkflows(env),
    ];
    if (controller.cron === "17 3 * * *") {
      tasks.push(cleanupSearchAnalytics(env, controller.scheduledTime));
    }
    await Promise.all(tasks);
  },
} satisfies ExportedHandler<ProcessingEnv>;

export async function cleanupExpiredUploads(
  env: ProcessingEnv,
  scheduledTime = Date.now(),
): Promise<{ selected: number; deleted: number }> {
  const graceSeconds = boundedNumber(
    env.UPLOAD_CLEANUP_GRACE_SECONDS,
    900,
    604_800,
  );
  const expiredBefore = new Date(
    scheduledTime - graceSeconds * 1_000,
  ).toISOString();
  const api = new InternalApi(env);
  const candidates = await api.expiredUploadCandidates({
    expiredBefore,
    limit: 100,
  });
  const claims = await Promise.allSettled(candidates.map((candidate) =>
    api.finalizeExpiredUpload(candidate.intentId, {
      expectedIntentVersion: candidate.intentVersion,
      expectedRecordingVersion: candidate.recordingVersion,
    })));
  const failedClaims = claims.filter((outcome) => outcome.status === "rejected");
  if (failedClaims.length > 0) {
    throw new Error(`expired_upload_claim_failed:${failedClaims.length}`);
  }

  // Deletion always follows a committed state transition. ABORTED/EXPIRED
  // recordings remain candidates until R2 confirms deletion, so transient R2
  // failures leak no data and are retried by the next scheduled run.
  const deletions = await api.pendingRecordingDeletions({ limit: 100 });
  const outcomes = await Promise.allSettled(deletions.map(async (candidate) => {
    await env.MEDIA.delete(candidate.objectKey);
    await api.confirmRecordingDeletion(candidate.recordingId, {
      expectedRecordingVersion: candidate.recordingVersion,
    });
  }));
  const failed = outcomes.filter((outcome) => outcome.status === "rejected");
  if (failed.length > 0) {
    throw new Error(`recording_cleanup_failed:${failed.length}`);
  }
  return { selected: candidates.length, deleted: deletions.length };
}

export async function reconcilePendingWorkflows(
  env: ProcessingEnv,
): Promise<{ selected: number; started: number }> {
  const api = new InternalApi(env);
  const pending = await api.pendingWorkflows({ limit: 100 });
  const outcomes = await Promise.allSettled(pending.map(async (job) => {
    if (job.type === "ingest") {
      const context = await api.jobContext(job.jobId);
      await env.INGEST_SESSION.createBatch([{
        id: job.workflowInstanceId,
        params: {
          jobId: job.jobId,
          projectId: job.projectId,
          recordingId: context.recordingId,
          objectKey: context.objectKey,
        },
        retention: workflowRetention,
      }]);
      return;
    }
    if (job.type === "enrich") {
      await env.ENRICH_SESSION.createBatch([{
        id: job.workflowInstanceId,
        params: { jobId: job.jobId, projectId: job.projectId },
        retention: workflowRetention,
      }]);
      return;
    }
    if (!job.publicationId) throw new Error("pending_publication_missing");
    await api.prepareLexicalWorkflow(
      job.jobId,
      job.publicationId,
      job.projectId,
      job.workflowInstanceId,
    );
    await env.INDEX_PUBLICATION.createBatch([{
      id: job.workflowInstanceId,
      params: {
        jobId: job.jobId,
        projectId: job.projectId,
        publicationId: job.publicationId,
      },
      retention: workflowRetention,
    }]);
  }));
  const failed = outcomes.filter((outcome) => outcome.status === "rejected");
  if (failed.length > 0) {
    throw new Error(`workflow_reconciliation_failed:${failed.length}`);
  }
  return { selected: pending.length, started: outcomes.length };
}

export async function cleanupSearchAnalytics(
  env: ProcessingEnv,
  scheduledTime = Date.now(),
): Promise<{ createdBefore: string; deletedQueryEvents: number }> {
  const retentionDays = boundedNumber(
    env.SEARCH_ANALYTICS_RETENTION_DAYS,
    1,
    365,
  );
  const createdBefore = new Date(
    scheduledTime - retentionDays * 86_400_000,
  ).toISOString();
  return new InternalApi(env).cleanupSearchAnalytics({ createdBefore });
}

async function startPublicationIndex(
  request: Request,
  env: ProcessingEnv,
  publicationId: string,
): Promise<Response> {
  requireBrowserMutation(request, env);
  await enforceMutationRateLimit(request, env);
  const body = await readJson<StartIndexRequest>(request, 16_384);
  if (
    !isUuid(publicationId)
    || !isUuid(body.jobId)
    || !isUuid(body.attemptId)
    || !isUuid(body.projectId)
  ) {
    throw new HttpError(400, "index_request_invalid");
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, body.projectId);
  const workflowInstanceId =
    `index-${body.jobId}-${body.attemptId}`;
  try {
    const prepared = await api.prepareLexicalWorkflow(
      body.jobId,
      publicationId,
      body.projectId,
      workflowInstanceId,
    );
    if (prepared.projectId !== body.projectId) {
      throw new HttpError(404, "publication_not_found");
    }
    await env.INDEX_PUBLICATION.createBatch([{
      id: workflowInstanceId,
      params: {
        jobId: body.jobId,
        projectId: body.projectId,
        publicationId,
      },
      retention: workflowRetention,
    }]);
  } catch {
    throw new Error("workflow_start_failed");
  }
  return json({
    publicationId,
    projectId: body.projectId,
    jobId: body.jobId,
    workflowInstanceId,
  }, 202, request, env);
}

async function retryIngestion(
  request: Request,
  env: ProcessingEnv,
  projectId: string,
): Promise<Response> {
  requireBrowserMutation(request, env);
  await enforceMutationRateLimit(request, env);
  const body = await readJson<RetryIngestionRequest>(request, 16_384);
  if (!isUuid(projectId) || !isUuid(body.jobId) || !isUuid(body.attemptId)) {
    throw new HttpError(400, "ingestion_retry_invalid");
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, projectId);
  const context = await api.jobContext(body.jobId);
  if (context.projectId !== projectId) {
    throw new HttpError(404, "project_not_found");
  }
  const workflowInstanceId = `ingest-${body.jobId}-${body.attemptId}`;
  const prepared = await api.retryIngest(body.jobId, {
    workflowInstanceId,
    expectedJobVersion: context.jobVersion,
    expectedProjectVersion: context.projectVersion,
  });
  try {
    await env.INGEST_SESSION.createBatch([{
      id: workflowInstanceId,
      params: {
        jobId: body.jobId,
        projectId,
        recordingId: prepared.recordingId,
        objectKey: prepared.objectKey,
      },
      retention: workflowRetention,
    }]);
  } catch {
    // The job retains this workflow ID and the scheduled reconciler retries it.
    throw new Error("workflow_start_failed");
  }
  return json({
    projectId,
    jobId: body.jobId,
    workflowInstanceId,
    projectStatus: "transcribing",
  }, 202, request, env);
}

async function hybridSearch(
  request: Request,
  env: ProcessingEnv,
  publicSlug: string | null,
): Promise<Response> {
  const rateLimit = await env.SEARCH_RATE_LIMITER.limit({
    key: `public-search:${requestActor(request)}`,
  });
  if (!rateLimit.success) {
    const response = json(
      {
        error: "rate_limited",
        message: "Too many requests. Try again later.",
      },
      429,
      request,
      env,
    );
    response.headers.set("retry-after", "60");
    return response;
  }
  const url = new URL(request.url);
  const query = url.searchParams.get("q")?.trim() ?? "";
  if (query.length < 2 || query.length > 300) {
    throw new HttpError(400, "search_query_invalid");
  }
  const api = new InternalApi(env);
  try {
    const dimensions = boundedNumber(env.EMBEDDING_DIMENSIONS, 1, 4_096);
    const [embedding] = await embedTexts(env, [query], dimensions);
    const result = await api.hybridSearch({
      query,
      publicSlug,
      organizationId: url.searchParams.get("organizationId"),
      sessionBody: url.searchParams.get("body"),
      dateFrom: url.searchParams.get("dateFrom"),
      dateTo: url.searchParams.get("dateTo"),
      speakerId: url.searchParams.get("speakerId"),
      agendaItemId: url.searchParams.get("agendaItemId"),
      language: url.searchParams.get("language"),
      kind: url.searchParams.get("kind"),
      limit: Number(url.searchParams.get("limit") ?? 20),
      offset: Number(url.searchParams.get("offset") ?? 0),
      embedding,
    });
    return json(result, 200, request, env);
  } catch {
    const path = publicSlug
      ? `/public/sessions/${encodeURIComponent(publicSlug)}/search?${url.searchParams}`
      : `/public/search?${url.searchParams}`;
    const fallback = await api.publicApi(path);
    const headers = corsHeaders(request, env);
    headers.set(
      "content-type",
      fallback.headers.get("content-type") ?? "application/json",
    );
    headers.set("cache-control", "no-store");
    return new Response(fallback.body, {
      status: fallback.status,
      headers,
    });
  }
}

async function createIntent(
  request: Request,
  env: ProcessingEnv,
): Promise<Response> {
  requireBrowserMutation(request, env);
  await enforceMutationRateLimit(request, env);
  const body = await readJson<CreateIntentRequest>(request, 32_768);
  validateCreateIntent(body, env);
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, body.projectId);

  const intentId = crypto.randomUUID();
  const recordingId = crypto.randomUUID();
  const jobId = crypto.randomUUID();
  const ttl = boundedNumber(env.UPLOAD_URL_TTL_SECONDS, 60, 3600);
  const expiresAt = new Date(Date.now() + ttl * 1000);
  const internal = await api.createUploadIntent({
    environment: env.ENVIRONMENT,
    projectId: body.projectId,
    intentId,
    recordingId,
    jobId,
    clientRequestId: body.clientRequestId,
    originalFilename: body.filename,
    mimeType: body.mimeType,
    sizeBytes: body.sizeBytes,
    checksumSha256: body.checksumSha256 ?? null,
    usagePermission: body.usagePermission,
    expiresAt: expiresAt.toISOString(),
  });
  const token = await createUploadToken(env.INTERNAL_API_HMAC_SECRET, {
    intentId: internal.intentId,
    projectId: internal.projectId,
    objectKey: internal.objectKey,
    sizeBytes: internal.sizeBytes,
    mimeType: internal.mimeType,
    expiresAtEpochSec: Math.floor(new Date(internal.expiresAt).getTime() / 1000),
  });

  let uploadUrl: string;
  let uploadMode: "presigned_put" | "worker_put";
  if (canPresign(env)) {
    uploadUrl = await presignUpload(
      env,
      internal.objectKey,
      internal.mimeType,
      ttl,
    );
    uploadMode = "presigned_put";
  } else if (env.ENVIRONMENT === "local") {
    const target = new URL(
      `/processing/uploads/${internal.intentId}`,
      request.url,
    );
    target.searchParams.set("token", token);
    uploadUrl = target.toString();
    uploadMode = "worker_put";
  } else {
    throw new Error("r2_presigning_not_configured");
  }

  return json({
    intentId: internal.intentId,
    recordingId: internal.recordingId,
    jobId: internal.jobId,
    uploadUrl,
    uploadMode,
    uploadToken: token,
    method: "PUT",
    allowedHeaders: { "content-type": internal.mimeType },
    expiresAt: internal.expiresAt,
    sizeBytes: internal.sizeBytes,
  }, 201, request, env);
}

async function publicationMedia(
  request: Request,
  env: ProcessingEnv,
  slug: string,
): Promise<Response> {
  const version = Number(new URL(request.url).searchParams.get("version"));
  if (!Number.isSafeInteger(version) || version <= 0) {
    throw new HttpError(404, "media_not_found");
  }
  const context = await new InternalApi(env).publicationMedia(slug, version);
  if (request.method === "HEAD") {
    const object = await env.MEDIA.head(context.objectKey);
    if (!object) throw new HttpError(404, "media_not_found");
    return mediaResponse(request, env, null, 200, context, {
      offset: 0,
      length: context.sizeBytes,
    });
  }
  if (canPresign(env)) {
    const url = await presignProviderRead(
      env,
      context.objectKey,
      boundedNumber(env.MEDIA_URL_TTL_SECONDS, 60, 3_600),
    );
    const headers = corsHeaders(request, env);
    headers.set("location", url);
    headers.set("cache-control", "private, no-store");
    headers.set("vary", "Origin, Range");
    return new Response(null, {
      status: 302,
      headers,
    });
  }
  if (env.ENVIRONMENT !== "local") {
    throw new Error("r2_presigning_not_configured");
  }
  const range = parseRange(request.headers.get("range"), context.sizeBytes);
  const object = await env.MEDIA.get(context.objectKey, {
    range: { offset: range.offset, length: range.length },
  });
  if (!object) throw new HttpError(404, "media_not_found");
  return mediaResponse(
    request,
    env,
    object.body,
    range.partial ? 206 : 200,
    context,
    range,
  );
}

async function privateRecordingUrl(
  request: Request,
  env: ProcessingEnv,
  projectId: string,
): Promise<Response> {
  if (!isUuid(projectId)) throw new HttpError(404, "project_not_found");
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, projectId);
  const context = await api.privateMedia(projectId);
  const ttl = boundedNumber(env.MEDIA_URL_TTL_SECONDS, 60, 3_600);
  if (canPresign(env)) {
    const url = await presignProviderRead(env, context.objectKey, ttl);
    return json({
      url,
      expiresAt: new Date(Date.now() + ttl * 1_000).toISOString(),
    }, 200, request, env);
  }
  if (env.ENVIRONMENT !== "local") {
    throw new Error("r2_presigning_not_configured");
  }
  return json({
    url: `/processing/projects/${encodeURIComponent(projectId)}/media`,
    expiresAt: null,
  }, 200, request, env);
}

async function privateRecordingMedia(
  request: Request,
  env: ProcessingEnv,
  projectId: string,
): Promise<Response> {
  if (!isUuid(projectId)) throw new HttpError(404, "project_not_found");
  if (env.ENVIRONMENT !== "local") {
    throw new HttpError(404, "private_media_proxy_disabled");
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, projectId);
  const context = await api.privateMedia(projectId);
  if (request.method === "HEAD") {
    const object = await env.MEDIA.head(context.objectKey);
    if (!object) throw new HttpError(404, "media_not_found");
    return mediaResponse(request, env, null, 200, context, {
      offset: 0,
      length: context.sizeBytes,
    }, "private, no-store");
  }
  const range = parseRange(request.headers.get("range"), context.sizeBytes);
  const object = await env.MEDIA.get(context.objectKey, {
    range: { offset: range.offset, length: range.length },
  });
  if (!object) throw new HttpError(404, "media_not_found");
  return mediaResponse(
    request,
    env,
    object.body,
    range.partial ? 206 : 200,
    context,
    range,
    "private, no-store",
  );
}

function mediaResponse(
  request: Request,
  env: ProcessingEnv,
  body: ReadableStream | null,
  status: number,
  context: {
    mimeType: string;
    sizeBytes: number;
    etag: string | null;
  },
  range: { offset: number; length: number },
  cacheControl = "public, max-age=300",
): Response {
  const headers = corsHeaders(request, env);
  headers.set("content-type", context.mimeType);
  headers.set("content-length", String(range.length));
  headers.set("accept-ranges", "bytes");
  headers.set("cache-control", cacheControl);
  if (context.etag) headers.set("etag", context.etag);
  if (status === 206) {
    headers.set(
      "content-range",
      `bytes ${range.offset}-${range.offset + range.length - 1}/${context.sizeBytes}`,
    );
  }
  return new Response(body, { status, headers });
}

export function parseRange(
  value: string | null,
  size: number,
): { offset: number; length: number; partial: boolean } {
  if (!value) return { offset: 0, length: size, partial: false };
  const match = /^bytes=(\d*)-(\d*)$/.exec(value.trim());
  if (!match) throw new HttpError(416, "range_invalid");
  let start: number;
  let end: number;
  if (!match[1]) {
    const suffix = Number(match[2]);
    if (!Number.isSafeInteger(suffix) || suffix <= 0) {
      throw new HttpError(416, "range_invalid");
    }
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Number(match[2]) : size - 1;
  }
  if (
    !Number.isSafeInteger(start)
    || !Number.isSafeInteger(end)
    || start < 0
    || end < start
    || start >= size
  ) {
    throw new HttpError(416, "range_invalid");
  }
  end = Math.min(end, size - 1);
  return { offset: start, length: end - start + 1, partial: true };
}

async function startEnrichment(
  request: Request,
  env: ProcessingEnv,
  projectId: string,
): Promise<Response> {
  requireBrowserMutation(request, env);
  await enforceMutationRateLimit(request, env);
  const body = await readJson<StartEnrichmentRequest>(request, 16_384);
  if (!isUuid(projectId) || !isUuid(body.jobId) || !isUuid(body.attemptId)) {
    throw new HttpError(400, "enrichment_request_invalid");
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, projectId);
  const workflowInstanceId = `enrich-${body.jobId}-${body.attemptId}`;
  try {
    const context = await api.enrichmentContext(body.jobId);
    await api.startEnrichment(body.jobId, {
      workflowInstanceId,
      expectedJobVersion: context.jobVersion,
      expectedProjectVersion: context.projectVersion,
    });
    await env.ENRICH_SESSION.createBatch([{
      id: workflowInstanceId,
      params: { jobId: body.jobId, projectId },
      retention: workflowRetention,
    }]);
  } catch {
    throw new Error("workflow_start_failed");
  }
  return json({
    projectId,
    jobId: body.jobId,
    workflowInstanceId,
    projectStatus: "enriching",
  }, 202, request, env);
}

async function uploadLocally(
  request: Request,
  env: ProcessingEnv,
  intentId: string,
): Promise<Response> {
  if (env.ENVIRONMENT !== "local") return new Response(null, { status: 404 });
  const token = new URL(request.url).searchParams.get("token");
  const payload = token
    ? await verifyUploadToken(env.INTERNAL_API_HMAC_SECRET, token)
    : null;
  if (!payload || payload.intentId !== intentId) {
    return json({ error: "upload_token_invalid" }, 401, request, env);
  }
  const length = Number(request.headers.get("content-length"));
  if (!Number.isSafeInteger(length) || length !== payload.sizeBytes || !request.body) {
    return json({ error: "upload_size_mismatch" }, 400, request, env);
  }
  const mimeType = request.headers.get("content-type")?.split(";")[0].trim();
  if (mimeType !== payload.mimeType) {
    return json({ error: "upload_type_mismatch" }, 400, request, env);
  }
  const object = await env.MEDIA.put(payload.objectKey, request.body, {
    httpMetadata: { contentType: payload.mimeType },
    customMetadata: { intentId: payload.intentId },
  });
  return json({ etag: object?.httpEtag ?? null }, 200, request, env);
}

async function completeIntent(
  request: Request,
  env: ProcessingEnv,
  intentId: string,
): Promise<Response> {
  requireBrowserMutation(request, env);
  const body = await readJson<CompleteIntentRequest>(request, 32_768);
  const payload = await verifyUploadToken(
    env.INTERNAL_API_HMAC_SECRET,
    body.uploadToken,
  );
  if (
    !payload
    || payload.intentId !== intentId
    || payload.projectId !== body.projectId
  ) {
    return json({ error: "upload_token_invalid" }, 401, request, env);
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, body.projectId);
  const object = await env.MEDIA.head(payload.objectKey);
  if (!object) return json({ error: "upload_missing" }, 409, request, env);
  if (object.size !== payload.sizeBytes) {
    return json({ error: "upload_size_mismatch" }, 409, request, env);
  }
  const storedType = object.httpMetadata?.contentType?.split(";")[0].trim();
  if (storedType && storedType !== payload.mimeType) {
    return json({ error: "upload_type_mismatch" }, 409, request, env);
  }
  const workflowInstanceId = `ingest-${intentId}`;
  const internal = await api.completeUpload(intentId, {
    etag: object.httpEtag,
    sizeBytes: object.size,
    mimeType: payload.mimeType,
    checksumSha256: body.checksumSha256 ?? null,
    workflowInstanceId,
    expectedIntentVersion: 0,
  });
  try {
    await env.INGEST_SESSION.createBatch([{
      id: workflowInstanceId,
      params: {
        jobId: internal.jobId,
        projectId: internal.projectId,
        recordingId: internal.recordingId,
        objectKey: internal.objectKey,
      },
      retention: workflowRetention,
    }]);
  } catch {
    // Spring has committed and the scheduled reconciler will retry this ID.
    throw new Error("workflow_start_failed");
  }
  return json({
    projectId: internal.projectId,
    recordingId: internal.recordingId,
    jobId: internal.jobId,
    projectStatus: "uploaded",
  }, 200, request, env);
}

async function abortIntent(
  request: Request,
  env: ProcessingEnv,
  intentId: string,
): Promise<Response> {
  requireBrowserMutation(request, env);
  const body = await readJson<CompleteIntentRequest>(request, 32_768);
  const payload = await verifyUploadToken(
    env.INTERNAL_API_HMAC_SECRET,
    body.uploadToken,
  );
  if (
    !payload
    || payload.intentId !== intentId
    || payload.projectId !== body.projectId
  ) {
    return json({ error: "upload_token_invalid" }, 401, request, env);
  }
  const api = new InternalApi(env);
  await api.assertProjectAccess(request, body.projectId);
  // Spring commits the state transition first. A completed intent is rejected
  // before R2 is touched, eliminating the verified-object deletion race.
  const deletion = await api.abortUpload(intentId);
  await env.MEDIA.delete(deletion.objectKey);
  await api.confirmRecordingDeletion(deletion.recordingId, {
    expectedRecordingVersion: deletion.recordingVersion,
  });
  return new Response(null, {
    status: 204,
    headers: corsHeaders(request, env),
  });
}

async function receiveElevenLabsWebhook(
  request: Request,
  env: ProcessingEnv,
): Promise<Response> {
  const maximum = boundedNumber(env.WEBHOOK_MAX_BYTES, 1_000_000, 32_000_000);
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maximum) {
    return json({ error: "webhook_too_large" }, 413, request, env);
  }
  const raw = await request.arrayBuffer();
  if (raw.byteLength > maximum) {
    return json({ error: "webhook_too_large" }, 413, request, env);
  }
  const valid = await verifyWebhook(
    raw,
    request.headers.get("elevenlabs-signature"),
    env.ELEVENLABS_WEBHOOK_SECRET,
  );
  if (!valid) return json({ error: "webhook_signature_invalid" }, 401, request, env);
  const webhook = parseWebhook(raw);
  const digest = await sha256(raw);
  const requestId = webhook.data.request_id;
  const jobId = webhook.data.webhook_metadata.jobId;
  const workflowInstanceId = webhook.data.webhook_metadata.workflowInstanceId;
  const artifactKey = `${env.ENVIRONMENT}/provider/elevenlabs/scribe-v2`
    + `/jobs/${jobId}/${requestId}/${digest}.json`;
  await env.MEDIA.put(artifactKey, raw, {
    httpMetadata: { contentType: "application/json" },
    customMetadata: { provider: "elevenlabs", payloadDigest: digest },
  });

  const api = new InternalApi(env);
  const delivery = await api.webhookReceived(jobId, {
    providerRequestId: requestId,
    payloadDigest: digest,
    artifactKey,
    workflowInstanceId,
  });
  try {
    const instance = await env.INGEST_SESSION.get(delivery.workflowInstanceId);
    await instance.sendEvent({
      type: "transcription_complete",
      payload: { artifactKey, payloadDigest: digest },
    });
  } catch {
    if (!delivery.duplicate) throw new Error("workflow_event_failed");
  }
  return json({ received: true, duplicate: delivery.duplicate }, 200, request, env);
}

function requireBrowserMutation(request: Request, env: ProcessingEnv): void {
  if (request.headers.get("origin") !== env.ALLOWED_ORIGIN) {
    throw new HttpError(403, "origin_not_allowed");
  }
  if (!hasValidCsrfEcho(request)) {
    throw new HttpError(403, "csrf_invalid");
  }
}

async function enforceMutationRateLimit(
  request: Request,
  env: ProcessingEnv,
): Promise<void> {
  const result = await env.MUTATION_RATE_LIMITER.limit({
    key: `browser-mutation:${requestActor(request)}`,
  });
  if (!result.success) {
    throw new HttpError(429, "rate_limited", 60);
  }
}

function requestActor(request: Request): string {
  return request.headers.get("cf-connecting-ip")
    ?? request.headers.get("x-forwarded-for")?.split(",", 1)[0]?.trim()
    ?? "anonymous";
}

function validateCreateIntent(
  body: CreateIntentRequest,
  env: ProcessingEnv,
): void {
  const maximum = Number(env.MAX_UPLOAD_BYTES);
  if (
    !isUuid(body.projectId)
    || !isUuid(body.clientRequestId)
    || typeof body.filename !== "string"
    || !body.filename
    || body.filename.length > 500
    || !Number.isSafeInteger(body.sizeBytes)
    || body.sizeBytes <= 0
    || body.sizeBytes > maximum
    || typeof body.mimeType !== "string"
    || !(body.mimeType.startsWith("audio/") || body.mimeType.startsWith("video/"))
    || typeof body.usagePermission !== "boolean"
    || (body.checksumSha256 != null && !/^[a-fA-F0-9]{64}$/.test(body.checksumSha256))
  ) throw new HttpError(400, "upload_intent_invalid");
}

async function readJson<T>(request: Request, maximumBytes: number): Promise<T> {
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maximumBytes) {
    throw new HttpError(413, "request_too_large");
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > maximumBytes) {
    throw new HttpError(413, "request_too_large");
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new HttpError(400, "json_invalid");
  }
}

function isUuid(value: unknown): value is string {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function boundedNumber(value: string, minimum: number, maximum: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return minimum;
  return Math.max(minimum, Math.min(maximum, parsed));
}

class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

function safeError(
  error: unknown,
  request: Request,
  env: ProcessingEnv,
): Response {
  if (error instanceof HttpError) {
    const response = json({ error: error.message }, error.status, request, env);
    if (error.retryAfterSeconds) {
      response.headers.set("retry-after", String(error.retryAfterSeconds));
    }
    return response;
  }
  if (error instanceof InternalApiError) {
    return json(
      { error: error.message },
      error.retryable ? 503 : error.status,
      request,
      env,
    );
  }
  if (error instanceof ProviderError) {
    return json({ error: error.code }, error.retryable ? 503 : 400, request, env);
  }
  const code = error instanceof Error && publicErrors.has(error.message)
    ? error.message
    : "processing_unavailable";
  return json({ error: code }, 503, request, env);
}

const publicErrors = new Set([
  "r2_presigning_not_configured",
  "workflow_start_failed",
  "workflow_event_failed",
]);

function preflight(request: Request, env: ProcessingEnv): Response {
  if (request.headers.get("origin") !== env.ALLOWED_ORIGIN) {
    return new Response(null, { status: 403 });
  }
  const headers = corsHeaders(request, env);
  headers.set("access-control-allow-methods", "GET,POST,PUT,DELETE,OPTIONS");
  headers.set("access-control-allow-headers", "content-type,x-xsrf-token");
  headers.set("access-control-max-age", "600");
  return new Response(null, { status: 204, headers });
}

function corsHeaders(request: Request, env: ProcessingEnv): Headers {
  const headers = new Headers({ vary: "Origin" });
  if (request.headers.get("origin") === env.ALLOWED_ORIGIN) {
    headers.set("access-control-allow-origin", env.ALLOWED_ORIGIN);
    headers.set("access-control-allow-credentials", "true");
  }
  return headers;
}

function json(
  value: unknown,
  status: number,
  request: Request,
  env: ProcessingEnv,
): Response {
  const headers = corsHeaders(request, env);
  headers.set("content-type", "application/json");
  headers.set("cache-control", "no-store");
  return new Response(JSON.stringify(value), { status, headers });
}
