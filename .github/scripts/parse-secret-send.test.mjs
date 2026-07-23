import assert from "node:assert/strict";
import test from "node:test";
import { parseSecretSend } from "./parse-secret-send.mjs";

const expected = new Map([
  ["GOOGLE_CLIENT_ID_DEV", "dev-client"],
  ["GOOGLE_CLIENT_SECRET_DEV", "dev-secret"],
  ["ELEVENLABS_API_KEY_DEV", "dev-eleven"],
]);

test("parses the documented JSON handoff", () => {
  const parsed = parseSecretSend(JSON.stringify(Object.fromEntries(expected)));
  assert.deepEqual(parsed, expected);
});

test("also accepts dotenv and YAML-style handoffs", () => {
  const parsed = parseSecretSend(`
    GOOGLE_CLIENT_ID_DEV=dev-client
    GOOGLE_CLIENT_SECRET_DEV: "dev-secret"
    - ELEVENLABS_API_KEY_DEV: dev-eleven
  `);
  assert.deepEqual(parsed, expected);
});
