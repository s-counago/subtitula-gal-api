import { randomBytes } from "node:crypto";
import {
  chmodSync,
  copyFileSync,
  existsSync,
  readFileSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const workerDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const apiDirectory = resolve(workerDirectory, "..");
const apiPath = resolve(apiDirectory, ".env");
const apiTemplatePath = resolve(apiDirectory, ".env.example");
const workerPath = resolve(workerDirectory, ".dev.vars");
const workerTemplatePath = resolve(workerDirectory, ".dev.vars.example");

const api = parseEnv(readOrTemplate(apiPath, apiTemplatePath));
const worker = parseEnv(readOrTemplate(workerPath, workerTemplatePath));
const apiSecret = api.values.get("INTERNAL_API_HMAC_SECRET");
const workerSecret = worker.values.get("INTERNAL_API_HMAC_SECRET");
if (apiSecret && workerSecret && apiSecret !== workerSecret) {
  throw new Error(
    "INTERNAL_API_HMAC_SECRET differs between .env and processing-worker/.dev.vars.",
  );
}
const sharedSecret =
  apiSecret || workerSecret || randomBytes(32).toString("hex");
api.values.set("INTERNAL_API_HMAC_SECRET", sharedSecret);
worker.values.set("INTERNAL_API_HMAC_SECRET", sharedSecret);

const elevenLabs = api.values.get("ELEVENLABS_API_KEY");
if (elevenLabs) worker.values.set("ELEVENLABS_API_KEY", elevenLabs);

writeEnv(apiPath, api);
writeEnv(workerPath, worker);
console.log("Prepared matching ignored local API/processing secrets.");

function readOrTemplate(target, template) {
  if (existsSync(target)) return readFileSync(target, "utf8");
  return existsSync(template) ? readFileSync(template, "utf8") : "";
}

function parseEnv(contents) {
  const lines = contents.replaceAll("\r\n", "\n").split("\n");
  const values = new Map();
  for (const line of lines) {
    const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
    if (match) values.set(match[1], match[2]);
  }
  return { lines, values };
}

function writeEnv(target, parsed) {
  const written = new Set();
  const lines = parsed.lines.map((line) => {
    const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
    if (!match || !parsed.values.has(match[1])) return line;
    written.add(match[1]);
    return `${match[1]}=${parsed.values.get(match[1])}`;
  });
  for (const [key, value] of parsed.values) {
    if (!written.has(key)) lines.push(`${key}=${value}`);
  }
  const output = `${lines.join("\n").trimEnd()}\n`;
  const temporary = `${target}.preparing`;
  writeFileSync(temporary, output, { encoding: "utf8", mode: 0o600 });
  chmodSync(temporary, 0o600);
  copyFileSync(temporary, target);
  chmodSync(target, 0o600);
  unlinkSync(temporary);
}
