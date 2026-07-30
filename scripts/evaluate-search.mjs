#!/usr/bin/env node

import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const DEFAULT_FIXTURE =
  "src/test/resources/fixtures/search-evaluation-seed.jsonl";

export function buildSearchUrl(origin, mode, evaluationCase) {
  const filters = evaluationCase.filters ?? {};
  const sessionSlug = filters.sessionSlug;
  const prefix = mode === "hybrid" ? "/processing" : "/public";
  const path = sessionSlug
    ? `${prefix}/sessions/${encodeURIComponent(sessionSlug)}/search`
    : `${prefix}/search`;
  const url = new URL(path, ensureTrailingSlash(origin));
  url.searchParams.set("q", evaluationCase.query);
  url.searchParams.set("limit", "20");

  const filterMapping = {
    organizationId: "organizationId",
    body: "body",
    dateFrom: "dateFrom",
    dateTo: "dateTo",
    speakerId: "speakerId",
    agendaItemId: "agendaItemId",
    language: "language",
    kind: "kind",
  };
  for (const [caseKey, parameter] of Object.entries(filterMapping)) {
    const value = filters[caseKey];
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(parameter, String(value));
    }
  }
  return url;
}

export function resultMatchesReference(result, reference) {
  const parts = reference.split(":");
  if (parts.length !== 3) return false;
  const [, expectedSession, expectedEvidence] = parts;
  if (result.publicSlug !== expectedSession) return false;
  return result.evidenceSegmentId === expectedEvidence
    || result.sourceEntityId === expectedEvidence;
}

export function computeMetrics(outcomes, mode) {
  const answerCases = outcomes.filter(
    (outcome) => !outcome.evaluationCase.expectedNoAnswer,
  );
  const noAnswerCases = outcomes.filter(
    (outcome) => outcome.evaluationCase.expectedNoAnswer,
  );
  const answerRanks = answerCases.map(firstRelevantRank);

  const relevantExpected = answerCases.reduce(
    (sum, outcome) =>
      sum + outcome.evaluationCase.relevantEvidenceRefs.length,
    0,
  );
  const relevantFound = answerCases.reduce(
    (sum, outcome) => sum + relevantReferencesFound(outcome),
    0,
  );
  const latencies = outcomes.map((outcome) => outcome.latencyMs);
  const requestFailures = outcomes
    .filter((outcome) => outcome.requestError)
    .map((outcome) => outcome.evaluationCase.id);

  return {
    schemaVersion: "1.0.0",
    mode,
    evaluatedAt: new Date().toISOString(),
    caseCount: outcomes.length,
    answerCaseCount: answerCases.length,
    noAnswerCaseCount: noAnswerCases.length,
    requestFailureCount: requestFailures.length,
    requestFailureCaseIds: requestFailures,
    recallAt20: divide(relevantFound, relevantExpected),
    taskSuccessAt20: average(answerRanks.map((rank) => rank !== null ? 1 : 0)),
    meanReciprocalRank: average(
      answerRanks.map((rank) => rank === null ? 0 : 1 / rank),
    ),
    ndcgAt20: average(answerCases.map(ndcgAt20)),
    falseNoAnswerRate: average(
      answerCases.map((outcome) => outcome.results.length === 0 ? 1 : 0),
    ),
    correctNoAnswerRate: average(
      noAnswerCases.map((outcome) => outcome.results.length === 0 ? 1 : 0),
    ),
    misleadingNoAnswerRate: average(
      noAnswerCases.map((outcome) => outcome.results.length > 0 ? 1 : 0),
    ),
    zeroResultRate: average(
      outcomes.map((outcome) => outcome.results.length === 0 ? 1 : 0),
    ),
    latencyMs: {
      p50: percentile(latencies, 0.5),
      p95: percentile(latencies, 0.95),
      maximum: latencies.length === 0 ? null : Math.max(...latencies),
    },
    byIntent: byIntent(outcomes),
  };
}

export async function evaluateCases({
  cases,
  origin,
  mode,
  fetchImpl = fetch,
  delayMs = 0,
}) {
  const outcomes = [];
  for (const evaluationCase of cases) {
    const started = performance.now();
    let results = [];
    let requestError = null;
    try {
      const response = await fetchImpl(
        buildSearchUrl(origin, mode, evaluationCase),
        { headers: { accept: "application/json" } },
      );
      if (!response.ok) {
        requestError = `http_${response.status}`;
      } else {
        const payload = await response.json();
        if (!Array.isArray(payload.results)) {
          requestError = "invalid_response";
        } else {
          results = payload.results.slice(0, 20);
        }
      }
    } catch {
      requestError = "network_error";
    }
    outcomes.push({
      evaluationCase,
      results,
      requestError,
      latencyMs: Math.max(0, Math.round(performance.now() - started)),
    });
    if (delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  return outcomes;
}

function firstRelevantRank(outcome) {
  for (let index = 0; index < outcome.results.length; index += 1) {
    if (outcome.evaluationCase.relevantEvidenceRefs.some(
      (reference) =>
        resultMatchesReference(outcome.results[index], reference),
    )) {
      return index + 1;
    }
  }
  return null;
}

function relevantReferencesFound(outcome) {
  return outcome.evaluationCase.relevantEvidenceRefs.filter(
    (reference) =>
      outcome.results.some((result) =>
        resultMatchesReference(result, reference)),
  ).length;
}

function ndcgAt20(outcome) {
  const relevant = outcome.evaluationCase.relevantEvidenceRefs;
  if (relevant.length === 0) return 0;
  let dcg = 0;
  for (let index = 0; index < outcome.results.length; index += 1) {
    if (relevant.some((reference) =>
      resultMatchesReference(outcome.results[index], reference))) {
      dcg += 1 / Math.log2(index + 2);
    }
  }
  let ideal = 0;
  const idealCount = Math.min(20, relevant.length);
  for (let index = 0; index < idealCount; index += 1) {
    ideal += 1 / Math.log2(index + 2);
  }
  return divide(dcg, ideal);
}

function byIntent(outcomes) {
  const groups = new Map();
  for (const outcome of outcomes) {
    const intent = outcome.evaluationCase.intent;
    const existing = groups.get(intent) ?? [];
    existing.push(outcome);
    groups.set(intent, existing);
  }
  return Object.fromEntries(
    [...groups.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([intent, group]) => {
        const expectedNoAnswer = group.every(
          (outcome) => outcome.evaluationCase.expectedNoAnswer,
        );
        const success = expectedNoAnswer
          ? average(group.map((outcome) => outcome.results.length === 0 ? 1 : 0))
          : average(group.map((outcome) =>
            firstRelevantRank(outcome) !== null ? 1 : 0));
        return [intent, { caseCount: group.length, taskSuccessAt20: success }];
      }),
  );
}

function percentile(values, fraction) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((left, right) => left - right);
  if (sorted.length === 1) return sorted[0];
  const index = (sorted.length - 1) * fraction;
  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  const weight = index - lower;
  return Math.round(
    sorted[lower] * (1 - weight) + sorted[upper] * weight,
  );
}

function average(values) {
  if (values.length === 0) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function divide(numerator, denominator) {
  return denominator === 0 ? null : numerator / denominator;
}

function ensureTrailingSlash(origin) {
  return origin.endsWith("/") ? origin : `${origin}/`;
}

async function readCases(path) {
  const content = await readFile(path, "utf8");
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

function parseArguments(argv) {
  const values = {
    fixture: DEFAULT_FIXTURE,
    origin: null,
    mode: "lexical",
    output: null,
    delayMs: 0,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--fixture") values.fixture = value;
    else if (key === "--origin") values.origin = value;
    else if (key === "--mode") values.mode = value;
    else if (key === "--output") values.output = value;
    else if (key === "--delay-ms") values.delayMs = Number(value);
    else throw new Error(`Unknown argument: ${key}`);
    index += 1;
  }
  if (!values.origin) {
    throw new Error("--origin is required");
  }
  if (!["lexical", "hybrid"].includes(values.mode)) {
    throw new Error("--mode must be lexical or hybrid");
  }
  if (!Number.isInteger(values.delayMs) || values.delayMs < 0) {
    throw new Error("--delay-ms must be a non-negative integer");
  }
  return values;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const cases = await readCases(options.fixture);
  const outcomes = await evaluateCases({
    cases,
    origin: options.origin,
    mode: options.mode,
    delayMs: options.delayMs,
  });
  const report = computeMetrics(outcomes, options.mode);
  const serialized = `${JSON.stringify(report, null, 2)}\n`;
  if (options.output) {
    await writeFile(options.output, serialized, "utf8");
  } else {
    process.stdout.write(serialized);
  }
  if (report.requestFailureCount > 0) {
    process.exitCode = 2;
  }
}

if (process.argv[1]
    && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch((error) => {
    process.stderr.write(`Search evaluation failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
