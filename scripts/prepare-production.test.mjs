import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';
import { productionConfigs } from './prepare-production.mjs';
const read = async path => ts.parseConfigFileTextToJson(path, await readFile(new URL(path, import.meta.url), 'utf8')).config;
const sources = {
  api: await read('../wrangler.jsonc'), processing: await read('../processing-worker/wrangler.jsonc'),
  web: { main: '.open-next/worker.js', assets: { directory: '.open-next/assets', binding: 'ASSETS' },
    env: { development: { name: 'subtitula-web-dev', vars: {}, services: [] } } },
};
const input = JSON.parse(await readFile(new URL('./fixtures/production-input.example.json', import.meta.url), 'utf8'));
const roots = { api: '/workspace/api', web: '/workspace/web' };
test('production preparation isolates resources, pins the approved image and leaves launch closed', () => {
  const result = productionConfigs(input, sources, roots);
  for (const name of ['api', 'processing', 'web']) {
    assert.equal(result[name].workers_dev, false);
    assert.equal(result[name].preview_urls, false);
    assert.deepEqual(result[name].routes, []);
    assert.equal(result[name].env, undefined);
  }
  assert.equal(result.api.containers[0].image, input.apiImage);
  assert.equal(result.api.vars.SPRING_PROFILES_ACTIVE, 'prod');
  assert.equal(result.api.vars.EMAIL_DELIVERY_REQUIRED, 'true');
  assert(Object.entries(result.api.vars).filter(([k]) => k.startsWith('CAPABILITY_')).every(([,v]) => v === 'false'));
  assert.deepEqual(result.processing.triggers.crons, []);
  assert.equal(result.processing.r2_buckets[0].bucket_name, 'subtitula-media-prod');
  assert(result.processing.workflows.every(w => w.name.endsWith('-prod')));
  assert.equal(result.processing.services[0].service, 'subtitula-api-prod');
  assert(result.web.services.every(s => s.service.endsWith('-prod')));
  assert.equal(result.api.vars.GOOGLE_REDIRECT_URI, 'https://app.example.invalid/backend/login/oauth2/code/google');
  assert.equal(result.buildEnvironment.NEXT_PUBLIC_API_URL, '/backend');
  assert.equal(sources.api.env.development.vars.SPRING_PROFILES_ACTIVE, 'dev');
});
test('rejects mutable images, development hosts and secret-bearing inputs', () => {
  for (const change of [{apiImage:'./Dockerfile'}, {apiImage:input.apiImage.replace(/@sha256:.+/, ':latest')}, {frontendHost:'subtitula-web-dev.s-counago00.workers.dev'}, {DB_PASSWORD:'must-not-be-accepted'}]) {
    assert.throws(() => productionConfigs({...input,...change},sources,roots));
  }
});
