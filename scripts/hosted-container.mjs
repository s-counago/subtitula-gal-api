import { readFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';

// Development only. Status does not start the Spring process. The token is
// supplied from the environment or an ignored file, never a command argument.
const command = process.argv[2];
if (!['status', 'suspend', 'resume', 'restart'].includes(command)) {
  throw new Error('Usage: node scripts/hosted-container.mjs status|suspend|resume|restart');
}
const secrets = process.env.HOSTED_OPERATIONS_TOKEN ? {} : JSON.parse(await readFile(
  new URL('../processing-worker/.wrangler/rollout/operations-secrets.json', import.meta.url), 'utf8'));
const token = process.env.HOSTED_OPERATIONS_TOKEN ?? secrets.HOSTED_OPERATIONS_TOKEN;
if (typeof token !== 'string' || token.length < 32) throw new Error('Missing operations credential.');
const origin = 'https://subtitula-api-dev.s-counago00.workers.dev';
async function operations(action = '') {
  const response = await fetch(`${origin}/__ops/container${action ? `/${action}` : ''}`, {
    method: action ? 'POST' : 'GET',
    headers: { authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(20000),
    redirect: 'error',
  });
  if (!response.ok) throw new Error(`Container operation returned HTTP ${response.status}.`);
  const status = await response.json();
  if (typeof status.suspended !== 'boolean' || typeof status.running !== 'boolean') {
    throw new Error('Unexpected container status response.');
  }
  return status;
}
async function suspend() {
  let status = await operations('suspend');
  const deadline = Date.now() + 90000;
  while (status.running && Date.now() < deadline) {
    await delay(2000);
    status = await operations();
  }
  if (!status.suspended || status.running) throw new Error('Suspension is set, but shutdown was not confirmed.');
  // Check the regular request gate, then prove that request did not wake Spring.
  const probe = await fetch(`${origin}/ping`, { signal: AbortSignal.timeout(10000) });
  if (probe.status !== 503) throw new Error('Suspended request gate did not return HTTP 503.');
  await delay(2000);
  status = await operations();
  if (!status.suspended || status.running) throw new Error('Container restarted during suspension.');
  return status;
}
async function resume() {
  const status = await operations('resume');
  if (status.suspended) throw new Error('Container remains suspended.');
  const started = Date.now();
  let healthy = false;
  while (Date.now() - started < 120000) {
    try {
      const response = await fetch(`${origin}/ping`, { signal: AbortSignal.timeout(40000) });
      await response.arrayBuffer();
      if (response.ok) { healthy = true; break; }
      if (![500, 502, 503, 504].includes(response.status)) {
        throw new Error(`Resume health check returned HTTP ${response.status}.`);
      }
    } catch (error) {
      if (error.name !== 'TimeoutError' && error.name !== 'TypeError') throw error;
    }
    await delay(2000);
  }
  if (!healthy) throw new Error('Resume was requested, but readiness was not confirmed within two minutes.');
  console.log(JSON.stringify({ event: 'development_api_ready', elapsedMs: Date.now() - started }));
  return operations();
}
if (command === 'restart' || command === 'suspend') console.log(JSON.stringify(await suspend()));
if (command === 'restart' || command === 'resume') console.log(JSON.stringify(await resume()));
if (command === 'status') console.log(JSON.stringify(await operations()));
