import { copyFileSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { parseSecretSend } from "./parse-secret-send.mjs";

const target = resolve(".env");
const supplied = parseSecretSend(readFileSync(0, "utf8"));
const required = new Map([
  ["GOOGLE_CLIENT_ID", supplied.get("GOOGLE_CLIENT_ID_LOCAL")],
  ["GOOGLE_CLIENT_SECRET", supplied.get("GOOGLE_CLIENT_SECRET_LOCAL")],
  ["ELEVENLABS_API_KEY", supplied.get("ELEVENLABS_API_KEY_LOCAL")],
]);

for (const [key, value] of required) {
  if (!value) throw new Error(`Local secret handoff is missing ${key}.`);
  if (/[\r\n]/.test(value)) throw new Error(`Local secret ${key} contains a newline.`);
}

let contents = readFileSync(target, "utf8");
for (const [key, value] of required) {
  const line = `${key}=${value}`;
  const pattern = new RegExp(`^${key}=.*$`, "m");
  contents = pattern.test(contents)
    ? contents.replace(pattern, line)
    : `${contents.trimEnd()}\n${line}\n`;
}

const temporary = `${target}.installing`;
writeFileSync(temporary, contents, { encoding: "utf8", mode: 0o600 });
copyFileSync(temporary, target);
unlinkSync(temporary);
console.log(`Installed ${required.size} secrets into the ignored local API environment.`);
