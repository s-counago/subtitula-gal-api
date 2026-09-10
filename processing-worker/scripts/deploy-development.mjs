import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const dryRun = process.argv.includes("--dry-run");
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID?.trim();

if (!dryRun && !/^[a-f0-9]{32}$/i.test(accountId ?? "")) {
  throw new Error(
    "CLOUDFLARE_ACCOUNT_ID is required to deploy the processing Worker.",
  );
}

const args = ["wrangler", "deploy", "--env", "development"];
if (dryRun) args.push("--dry-run");
if (accountId) {
  args.push(
    "--var",
    `R2_S3_ENDPOINT:https://${accountId}.r2.cloudflarestorage.com`,
  );
}

const result = spawnSync(
  process.execPath,
  [
    fileURLToPath(new URL("../node_modules/wrangler/bin/wrangler.js", import.meta.url)),
    ...args.slice(1),
  ],
  { encoding: "utf8", env: process.env, stdio: "inherit" },
);

if (result.error) throw result.error;
process.exit(result.status ?? 1);
