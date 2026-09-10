import { Container, getContainer } from "@cloudflare/containers";
import { handleOperations, OPERATIONS_PATH } from "./operations";

interface RuntimeSecrets {
  DB_URL: string;
  DB_USER: string;
  DB_PASSWORD: string;
  GOOGLE_CLIENT_ID: string;
  GOOGLE_CLIENT_SECRET: string;
  ELEVENLABS_API_KEY: string;
  INTERNAL_API_HMAC_SECRET: string;
  SEARCH_ANALYTICS_HMAC_SECRET?: string;
  HOSTED_OPERATIONS_TOKEN?: string;
  SMTP_PASSWORD?: string;
  APP_EMAIL_FROM?: string;
  SMTP_HOST?: string;
  SMTP_PORT?: string;
  SMTP_USERNAME?: string;
  SMTP_SSL_ENABLED?: string;
  SMTP_STARTTLS_ENABLED?: string;
  SEARCH_MINIMUM_SEMANTIC_SIMILARITY?: string;
}

type WorkerEnv = Env & RuntimeSecrets;

export class SubtitulaApiContainer extends Container<WorkerEnv> {
  defaultPort = 8080;
  requiredPorts = [8080];
  pingEndpoint = "/ping";
  sleepAfter = "10m";
  enableInternet = true;

  async operationsStatus(): Promise<{ suspended: boolean; running: boolean }> {
    return {
      suspended: (await this.ctx.storage.get<boolean>("operations:suspended")) === true,
      running: this.ctx.container?.running === true,
    };
  }

  async setSuspended(suspended: boolean): Promise<void> {
    await this.ctx.storage.put("operations:suspended", suspended);
    if (suspended) await this.stop();
  }

  override async fetch(request: Request): Promise<Response> {
    if (await this.ctx.storage.get<boolean>("operations:suspended")) {
      return Response.json({ error: "development_suspended" }, {
        status: 503,
        headers: { "cache-control": "no-store", "retry-after": "3600" },
      });
    }
    return super.fetch(request);
  }

  envVars: Record<string, string> = {
    SPRING_PROFILES_ACTIVE: this.env.SPRING_PROFILES_ACTIVE,
    DB_URL: this.env.DB_URL,
    DB_USER: this.env.DB_USER,
    DB_PASSWORD: this.env.DB_PASSWORD,
    DB_POOL_MAXIMUM_SIZE: this.env.DB_POOL_MAXIMUM_SIZE ?? "5",
    DB_POOL_MINIMUM_IDLE: this.env.DB_POOL_MINIMUM_IDLE ?? "0",
    DB_POOL_CONNECTION_TIMEOUT_MS: this.env.DB_POOL_CONNECTION_TIMEOUT_MS ?? "10000",
    GOOGLE_CLIENT_ID: this.env.GOOGLE_CLIENT_ID,
    GOOGLE_CLIENT_SECRET: this.env.GOOGLE_CLIENT_SECRET,
    GOOGLE_REDIRECT_URI: this.env.GOOGLE_REDIRECT_URI,
    ELEVENLABS_API_KEY: this.env.ELEVENLABS_API_KEY,
    INTERNAL_API_HMAC_SECRET: this.env.INTERNAL_API_HMAC_SECRET,
    ELEVENLABS_MODEL_ID: this.env.ELEVENLABS_MODEL_ID,
    ELEVENLABS_LANGUAGE_HINT: this.env.ELEVENLABS_LANGUAGE_HINT,
    CAPABILITY_DURABLE_INSTITUTIONAL_UPLOAD:
      this.env.CAPABILITY_DURABLE_INSTITUTIONAL_UPLOAD ?? "false",
    CAPABILITY_NORMALIZED_TRANSCRIPT:
      this.env.CAPABILITY_NORMALIZED_TRANSCRIPT ?? "true",
    CAPABILITY_EXCEPTION_REVIEW:
      this.env.CAPABILITY_EXCEPTION_REVIEW ?? "false",
    CAPABILITY_AUTOMATIC_AGENDA:
      this.env.CAPABILITY_AUTOMATIC_AGENDA ?? "false",
    CAPABILITY_STRUCTURED_GUIDE:
      this.env.CAPABILITY_STRUCTURED_GUIDE ?? "false",
    CAPABILITY_PUBLIC_PUBLICATION:
      this.env.CAPABILITY_PUBLIC_PUBLICATION ?? "false",
    CAPABILITY_LEXICAL_SEARCH:
      this.env.CAPABILITY_LEXICAL_SEARCH ?? "false",
    CAPABILITY_HYBRID_SEARCH:
      this.env.CAPABILITY_HYBRID_SEARCH ?? "false",
    SEARCH_EMBEDDING_MODEL:
      this.env.SEARCH_EMBEDDING_MODEL ?? "@cf/baai/bge-m3",
    SEARCH_EMBEDDING_DIMENSIONS:
      this.env.SEARCH_EMBEDDING_DIMENSIONS ?? "1024",
    SEARCH_MINIMUM_SEMANTIC_SIMILARITY:
      this.env.SEARCH_MINIMUM_SEMANTIC_SIMILARITY ?? "0.32",
    SEARCH_EMBEDDING_MICRO_USD_PER_MILLION_INPUT_TOKENS:
      this.env.SEARCH_EMBEDDING_MICRO_USD_PER_MILLION_INPUT_TOKENS ?? "12000",
    SEARCH_ESTIMATED_CHARS_PER_TOKEN:
      this.env.SEARCH_ESTIMATED_CHARS_PER_TOKEN ?? "3",
    ...(this.env.SEARCH_ANALYTICS_HMAC_SECRET
      ? {
          SEARCH_ANALYTICS_HMAC_SECRET:
            this.env.SEARCH_ANALYTICS_HMAC_SECRET,
        }
      : {}),
    CORS_ORIGINS: this.env.CORS_ORIGINS,
    FRONTEND_URL: this.env.FRONTEND_URL,
    EMAIL_PROVIDER: this.env.EMAIL_PROVIDER ?? "disabled",
    EMAIL_DELIVERY_REQUIRED: this.env.EMAIL_DELIVERY_REQUIRED ?? "false",
    ...(this.env.SMTP_PASSWORD ? { SMTP_PASSWORD: this.env.SMTP_PASSWORD } : {}),
    ...(this.env.APP_EMAIL_FROM ? { APP_EMAIL_FROM: this.env.APP_EMAIL_FROM } : {}),
    ...(this.env.SMTP_HOST ? { SMTP_HOST: this.env.SMTP_HOST } : {}),
    ...(this.env.SMTP_PORT ? { SMTP_PORT: this.env.SMTP_PORT } : {}),
    ...(this.env.SMTP_USERNAME ? { SMTP_USERNAME: this.env.SMTP_USERNAME } : {}),
    ...(this.env.SMTP_SSL_ENABLED ? { SMTP_SSL_ENABLED: this.env.SMTP_SSL_ENABLED } : {}),
    ...(this.env.SMTP_STARTTLS_ENABLED
      ? { SMTP_STARTTLS_ENABLED: this.env.SMTP_STARTTLS_ENABLED } : {}),
  };

  override async onStart(): Promise<void> {
    // A request admitted before suspension may finish starting afterwards.
    if (await this.ctx.storage.get<boolean>("operations:suspended")) {
      await this.stop();
      return;
    }
    console.log(JSON.stringify({ event: "subtitula_api_container_started" }));
  }

  override onStop({ exitCode, reason }: { exitCode: number; reason: string }): void {
    console.log(JSON.stringify({
      event: "subtitula_api_container_stopped",
      exitCode,
      reason,
    }));
  }

  override onError(error: unknown): never {
    console.error(JSON.stringify({
      event: "subtitula_api_container_error",
      error: error instanceof Error ? error.message : String(error),
    }));
    throw error;
  }
}

export default {
  async fetch(request: Request, env: WorkerEnv): Promise<Response> {
    if (new URL(request.url).pathname.startsWith(OPERATIONS_PATH)) {
      if (env.SPRING_PROFILES_ACTIVE !== "dev") {
        return new Response(null, { status: 404 });
      }
      return handleOperations(request, env.HOSTED_OPERATIONS_TOKEN,
        () => getContainer(env.API_CONTAINER, "development-singleton"));
    }
    const container = getContainer(env.API_CONTAINER, "development-singleton");
    return container.fetch(request);
  },
} satisfies ExportedHandler<WorkerEnv>;
