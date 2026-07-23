import { spawnSync } from "node:child_process";
import { parseSecretSend } from "./parse-secret-send.mjs";

const sendUrl = process.env.BOOTSTRAP_BITWARDEN_SEND_URL;
const inlinePayload = process.env.BOOTSTRAP_SERVICE_SECRETS_JSON;
if (!sendUrl && !inlinePayload) {
  console.log("No bootstrap secret payload; existing Worker secrets were preserved.");
  process.exit(0);
}

let sendText = inlinePayload;
if (!sendText) {
  const bitwardenCli = process.env.BITWARDEN_CLI_PATH;
  if (!bitwardenCli) throw new Error("BITWARDEN_CLI_PATH is missing.");

  const configured = spawnSync(
    bitwardenCli,
    ["config", "server", "https://vault.bitwarden.eu"],
    { encoding: "utf8", env: process.env },
  );
  if (configured.status !== 0) {
    throw new Error("Bitwarden CLI could not select the EU vault.");
  }

  const received = spawnSync(bitwardenCli, ["receive", sendUrl], {
    encoding: "utf8",
    env: process.env,
    maxBuffer: 1024 * 1024,
  });
  if (received.status !== 0 || !received.stdout) {
    throw new Error("Bitwarden Send could not be received.");
  }
  sendText = received.stdout;
}

const supplied = parseSecretSend(sendText);

const requiredSendKeys = [
  "GOOGLE_CLIENT_ID_DEV",
  "GOOGLE_CLIENT_SECRET_DEV",
  "ELEVENLABS_API_KEY_DEV",
];
for (const key of requiredSendKeys) {
  if (!supplied.get(key)) throw new Error(`Bitwarden Send is missing required field ${key}.`);
}

const requiredDatabaseKeys = ["BOOTSTRAP_DB_URL", "BOOTSTRAP_DB_USER", "BOOTSTRAP_DB_PASSWORD"];
for (const key of requiredDatabaseKeys) {
  if (!process.env[key]) throw new Error(`GitHub bootstrap secret ${key} is missing.`);
}

const workerSecrets = {
  DB_URL: process.env.BOOTSTRAP_DB_URL,
  DB_USER: process.env.BOOTSTRAP_DB_USER,
  DB_PASSWORD: process.env.BOOTSTRAP_DB_PASSWORD,
  GOOGLE_CLIENT_ID: supplied.get("GOOGLE_CLIENT_ID_DEV"),
  GOOGLE_CLIENT_SECRET: supplied.get("GOOGLE_CLIENT_SECRET_DEV"),
  ELEVENLABS_API_KEY: supplied.get("ELEVENLABS_API_KEY_DEV"),
};

const uploaded = spawnSync(
  process.platform === "win32" ? "npx.cmd" : "npx",
  ["wrangler", "secret", "bulk", "--env", "development"],
  {
    input: JSON.stringify(workerSecrets),
    encoding: "utf8",
    env: process.env,
    stdio: ["pipe", "inherit", "inherit"],
  },
);
if (uploaded.status !== 0) {
  throw new Error("Cloudflare rejected the development Worker secret upload.");
}

console.log(`Installed ${Object.keys(workerSecrets).length} development Worker secrets.`);
