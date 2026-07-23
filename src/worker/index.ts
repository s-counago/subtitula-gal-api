import { Container, getContainer } from "@cloudflare/containers";

interface RuntimeSecrets {
  DB_URL: string;
  DB_USER: string;
  DB_PASSWORD: string;
  GOOGLE_CLIENT_ID: string;
  GOOGLE_CLIENT_SECRET: string;
  ELEVENLABS_API_KEY: string;
}

type WorkerEnv = Env & RuntimeSecrets;

export class SubtitulaApiContainer extends Container<WorkerEnv> {
  defaultPort = 8080;
  requiredPorts = [8080];
  pingEndpoint = "/ping";
  sleepAfter = "10m";
  enableInternet = true;

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
    ELEVENLABS_API_KEY: this.env.ELEVENLABS_API_KEY,
    ELEVENLABS_MODEL_ID: this.env.ELEVENLABS_MODEL_ID,
    ELEVENLABS_LANGUAGE_HINT: this.env.ELEVENLABS_LANGUAGE_HINT,
    CORS_ORIGINS: this.env.CORS_ORIGINS,
    FRONTEND_URL: this.env.FRONTEND_URL,
    EMAIL_PROVIDER: this.env.EMAIL_PROVIDER ?? "disabled",
    EMAIL_DELIVERY_REQUIRED: this.env.EMAIL_DELIVERY_REQUIRED ?? "false",
  };

  override onStart(): void {
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
    const container = getContainer(env.API_CONTAINER, "development-singleton");
    return container.fetch(request);
  },
} satisfies ExportedHandler<WorkerEnv>;
