#!/usr/bin/env node
// Offline preparation only: this module has no network or deployment operations.
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ts from 'typescript';

const apiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webRoot = resolve(apiRoot, '../subtitula-gal');
const allowed = new Set(['accountId', 'frontendHost', 'apiHost', 'processingHost', 'sender', 'apiImage', 'elevenlabsWebhookId']);

export function productionConfigs(input, sources, roots) {
  if (Object.keys(input).some(key => !allowed.has(key))) throw new Error('Only documented non-secret production inputs are accepted.');
  if (!/^[a-f0-9]{32}$/.test(input.accountId ?? '')) throw new Error('A Cloudflare account ID is required.');
  for (const name of ['frontendHost', 'apiHost', 'processingHost']) {
    const value = input[name];
    if (typeof value !== 'string' || !/^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}$/.test(value)
        || value.endsWith('.workers.dev') || value.includes('localhost')) throw new Error(`A future custom hostname is required: ${name}`);
  }
  if (new Set([input.frontendHost, input.apiHost, input.processingHost]).size !== 3) throw new Error('Production hostnames must be distinct.');
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.sender ?? '')) throw new Error('A future verified sender address is required.');
  if (!new RegExp(`^registry\\.cloudflare\\.com/${input.accountId}/[a-z0-9-]+@sha256:[a-f0-9]{64}$`).test(input.apiImage ?? '')) {
    throw new Error('Use the immutable registry digest already validated in development, not a tag or Dockerfile.');
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(input.elevenlabsWebhookId ?? '')) throw new Error('A separate production webhook ID is required.');
  const flatten = source => {
    const value = structuredClone({ ...source, ...source.env.development });
    delete value.env;
    value.workers_dev = false;
    value.preview_urls = false;
    value.routes = [];
    value.account_id = input.accountId;
    return value;
  };
  const api = flatten(sources.api), processing = flatten(sources.processing), web = flatten(sources.web);
  api.name = 'subtitula-api-prod';
  api.main = resolve(roots.api, 'src/worker/index.ts');
  api.$schema = resolve(roots.api, 'node_modules/wrangler/config-schema.json');
  api.containers = api.containers.map(container => ({ ...container, image: input.apiImage }));
  api.vars = {
    ...api.vars, SPRING_PROFILES_ACTIVE: 'prod',
    EMAIL_PROVIDER: 'smtp', EMAIL_DELIVERY_REQUIRED: 'true', APP_EMAIL_FROM: input.sender,
    CORS_ORIGINS: `https://${input.frontendHost}`, FRONTEND_URL: `https://${input.frontendHost}`,
    GOOGLE_REDIRECT_URI: `https://${input.frontendHost}/backend/login/oauth2/code/google`,
  };
  for (const key of Object.keys(api.vars)) if (key.startsWith('CAPABILITY_')) api.vars[key] = 'false';
  processing.name = 'subtitula-processing-prod';
  processing.main = resolve(roots.api, 'processing-worker/src/index.ts');
  processing.$schema = resolve(roots.api, 'processing-worker/node_modules/wrangler/config-schema.json');
  processing.vars = {
    ...processing.vars, ENVIRONMENT: 'production', API_ORIGIN: `https://${input.apiHost}`,
    ALLOWED_ORIGIN: `https://${input.frontendHost}`, R2_BUCKET_NAME: 'subtitula-media-prod',
    R2_S3_ENDPOINT: `https://${input.accountId}.r2.cloudflarestorage.com`,
    ELEVENLABS_WEBHOOK_ID: input.elevenlabsWebhookId,
  };
  processing.services = [{ binding: 'API_SERVICE', service: api.name }];
  processing.r2_buckets = [{ binding: 'MEDIA', bucket_name: 'subtitula-media-prod' }];
  processing.workflows = processing.workflows.map(workflow => ({ ...workflow, name: workflow.name.replace(/-dev$/, '-prod') }));
  processing.ratelimits = processing.ratelimits.map((limit, index) => ({ ...limit, namespace_id: String(10005 + index) }));
  processing.triggers = { crons: [] };
  web.name = 'subtitula-web-prod';
  web.main = resolve(roots.web, '.open-next/worker.js');
  web.$schema = resolve(roots.web, 'node_modules/wrangler/config-schema.json');
  web.assets.directory = resolve(roots.web, '.open-next/assets');
  web.vars = { API_ORIGIN: `https://${input.apiHost}`, PROCESSING_ORIGIN: `https://${input.processingHost}` };
  web.services = [{ binding: 'API_SERVICE', service: api.name }, { binding: 'PROCESSING_SERVICE', service: processing.name }];
  const buildEnvironment = {
    NEXT_PUBLIC_API_URL: '/backend', NEXT_PUBLIC_PROCESSING_URL: '/processing',
    NEXT_PUBLIC_SITE_URL: `https://${input.frontendHost}`,
    NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED: 'true', NEXT_PUBLIC_GOOGLE_AUTH_ENABLED: 'true',
  };
  return { api, processing, web, buildEnvironment };
}

async function readConfig(path) {
  const parsed = ts.parseConfigFileTextToJson(path, await readFile(path, 'utf8'));
  if (parsed.error) throw new Error(`Invalid source configuration: ${path}`);
  return parsed.config;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv.length !== 3) throw new Error('Usage: node scripts/prepare-production.mjs <non-secret-input.json>');
  const input = JSON.parse(await readFile(resolve(process.argv[2]), 'utf8'));
  const sources = {
    api: await readConfig(resolve(apiRoot, 'wrangler.jsonc')),
    processing: await readConfig(resolve(apiRoot, 'processing-worker/wrangler.jsonc')),
    web: await readConfig(resolve(webRoot, 'wrangler.jsonc')),
  };
  const result = productionConfigs(input, sources, { api: apiRoot, web: webRoot });
  const output = resolve(apiRoot, '.wrangler/production');
  await mkdir(output, { recursive: true });
  for (const [name, value] of Object.entries(result)) await writeFile(resolve(output, `${name}.json`), JSON.stringify(value, null, 2) + '\n');
  console.log('Prepared API, processor, frontend and build settings in .wrangler/production. Routes, schedules and all capabilities remain disabled. Nothing was deployed.');
}
