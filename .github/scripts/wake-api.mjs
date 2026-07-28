// Wakes the hosted dev Container so a testing session does not start on a cold
// request. The Container scales to zero (`sleepAfter = "10m"` in src/worker/index.ts),
// and a cold `/ping` has measured about 24 seconds, so this polls until Spring answers
// rather than failing on the first slow attempt.
//
//   npm run wake
//   npm run wake -- http://localhost:8080

const DEFAULT_ORIGIN = "https://subtitula-api-dev.s-counago00.workers.dev";
const RETRY_DELAY_MS = 3_000;

const origin = (process.argv[2] ?? process.env.API_ORIGIN ?? DEFAULT_ORIGIN).replace(/\/+$/, "");
const deadlineMs = Number(process.env.WAKE_TIMEOUT_MS ?? 180_000);
const attemptTimeoutMs = Number(process.env.WAKE_ATTEMPT_TIMEOUT_MS ?? 60_000);

const startedAt = Date.now();
const seconds = () => ((Date.now() - startedAt) / 1000).toFixed(1);
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function wake() {
  let attempt = 0;

  while (Date.now() - startedAt < deadlineMs) {
    attempt += 1;
    try {
      const response = await fetch(`${origin}/ping`, {
        signal: AbortSignal.timeout(attemptTimeoutMs),
        headers: { "user-agent": "subtitula-wake" },
      });
      const body = (await response.text()).trim();

      if (response.ok) {
        console.log(`Attempt ${attempt}: ${response.status} after ${seconds()}s — ${body}`);
        return attempt === 1 && Date.now() - startedAt < 3_000 ? "already-awake" : "woken";
      }

      // A 5xx here is usually the Container still booting, so it is worth retrying.
      console.log(`Attempt ${attempt}: ${response.status} ${response.statusText} after ${seconds()}s — retrying.`);
    } catch (error) {
      const reason = error.name === "TimeoutError" ? "no response" : error.message;
      console.log(`Attempt ${attempt}: ${reason} after ${seconds()}s — retrying.`);
    }

    await wait(RETRY_DELAY_MS);
  }

  return "timed-out";
}

console.log(`Waking ${origin}/ping — up to ${Math.round(deadlineMs / 1000)}s.`);
const outcome = await wake();

if (outcome === "timed-out") {
  console.error(`No answer from ${origin}/ping within ${seconds()}s.`);
  console.error("A boot failure is indistinguishable from a slow start here: run `npx wrangler tail --env development` for the Container logs.");
  // Setting the code rather than calling process.exit(): exiting while fetch still
  // holds a pooled socket trips a libuv assertion on Windows and reports 127.
  process.exitCode = 1;
} else {
  console.log(outcome === "already-awake" ? "Container was already awake." : "Container is awake.");
  console.log("It scales back to zero after 10 minutes with no requests.");
}
