export interface RuntimeSecrets {
  ELEVENLABS_API_KEY: string;
  ELEVENLABS_WEBHOOK_SECRET: string;
  INTERNAL_API_HMAC_SECRET: string;
  R2_S3_ACCESS_KEY_ID: string;
  R2_S3_SECRET_ACCESS_KEY: string;
}

interface RuntimeConfig {
  ENVIRONMENT: string;
  API_ORIGIN: string;
  ALLOWED_ORIGIN: string;
  R2_BUCKET_NAME: string;
  R2_S3_ENDPOINT: string;
  ELEVENLABS_API_BASE: string;
  ELEVENLABS_MODEL_ID: string;
  ELEVENLABS_SCRIBE_MICRO_USD_PER_HOUR: string;
  ELEVENLABS_LANGUAGE_HINT: string;
  ELEVENLABS_WEBHOOK_ID: string;
  MAX_UPLOAD_BYTES: string;
  UPLOAD_URL_TTL_SECONDS: string;
  UPLOAD_CLEANUP_GRACE_SECONDS: string;
  SEARCH_ANALYTICS_RETENTION_DAYS: string;
  PROVIDER_SOURCE_TTL_SECONDS: string;
  WEBHOOK_MAX_BYTES: string;
  GUIDE_MODEL_ID: string;
  GUIDE_PROMPT_VERSION: string;
  GUIDE_SCHEMA_VERSION: string;
  GUIDE_MAX_WINDOW_CHARS: string;
  GUIDE_INPUT_MICRO_USD_PER_MILLION_TOKENS: string;
  GUIDE_OUTPUT_MICRO_USD_PER_MILLION_TOKENS: string;
  MEDIA_URL_TTL_SECONDS: string;
  EMBEDDING_MODEL_ID: string;
  EMBEDDING_DIMENSIONS: string;
  EMBEDDING_BATCH_SIZE: string;
  EMBEDDING_INPUT_MICRO_USD_PER_MILLION_TOKENS: string;
  ESTIMATED_CHARS_PER_TOKEN: string;
}

// Wrangler narrows vars to the selected environment's literal values. Runtime
// code also runs against the local/default environment, so widen only vars and
// retain generated binding types.
export type ProcessingEnv =
  & Omit<Env, keyof RuntimeConfig>
  & RuntimeConfig
  & RuntimeSecrets;

export type IngestWorkflowParams = {
  jobId: string;
  projectId: string;
  recordingId: string;
  objectKey: string;
};

export type ProviderArtifactEvent = {
  artifactKey: string;
  payloadDigest: string;
};

export type EnrichWorkflowParams = {
  jobId: string;
  projectId: string;
};

export type IndexWorkflowParams = {
  jobId: string;
  projectId: string;
  publicationId: string;
};

export type EmbeddingIndexContext = {
  jobId: string;
  projectId: string;
  publicationId: string;
  modelVersion: string;
  dimensions: number;
  jobVersion: number;
  projectVersion: number;
  documents: Array<{
    id: string;
    contentHash: string;
    text: string;
  }>;
};

export type EnrichmentContext = {
  jobId: string;
  projectId: string;
  transcriptRevisionId: string;
  transcriptContentHash: string;
  languageCode: string | null;
  projectName: string;
  sessionDate: string | null;
  sessionBody: string | null;
  jobVersion: number;
  projectVersion: number;
  segments: EnrichmentSegment[];
  agendaItems: EnrichmentAgendaItem[];
};

export type EnrichmentSegment = {
  id: string;
  sequence: number;
  startMs: number;
  endMs: number;
  speakerId: string | null;
  speakerLabel: string;
  text: string;
};

export type EnrichmentAgendaItem = {
  id: string;
  ordinal: number;
  externalIdentifier: string | null;
  title: string;
  description: string | null;
};

export type JobContext = {
  jobId: string;
  projectId: string;
  recordingId: string;
  objectKey: string;
  mimeType: string;
  sizeBytes: number;
  checksumSha256: string | null;
  languageCode: string | null;
  state: string;
  stage: string;
  jobVersion: number;
  projectVersion: number;
};

export type InternalUploadIntent = {
  intentId: string;
  projectId: string;
  recordingId: string;
  jobId: string;
  objectKey: string;
  mimeType: string;
  sizeBytes: number;
  checksumSha256: string | null;
  expiresAt: string;
  state: string;
  intentVersion: number;
  jobVersion: number;
  projectVersion: number;
};

export type ExpiredUploadCandidate = {
  intentId: string;
  projectId: string;
  recordingId: string;
  objectKey: string;
  expiresAt: string;
  intentVersion: number;
  recordingVersion: number;
};
