import assert from "node:assert/strict";
import test from "node:test";
import {
  buildSearchUrl,
  computeMetrics,
  resultMatchesReference,
} from "./evaluate-search.mjs";

test("builds lexical and within-session hybrid URLs with supported filters", () => {
  const global = buildSearchUrl("http://localhost:8080", "lexical", {
    query: "auga e orzamentos",
    filters: { body: "Concello", language: "glg" },
  });
  assert.equal(global.pathname, "/public/search");
  assert.equal(global.searchParams.get("q"), "auga e orzamentos");
  assert.equal(global.searchParams.get("body"), "Concello");

  const session = buildSearchUrl("http://localhost:3000", "hybrid", {
    query: "cando se falou?",
    filters: { sessionSlug: "pleno-xuno", kind: "evidence" },
  });
  assert.equal(
    session.pathname,
    "/processing/sessions/pleno-xuno/search",
  );
  assert.equal(session.searchParams.get("kind"), "evidence");
});

test("matches only the labelled session and evidence identity", () => {
  const result = {
    publicSlug: "pleno-xuno",
    evidenceSegmentId: "evidence-auga-1",
    sourceEntityId: "topic-1",
  };
  assert.equal(
    resultMatchesReference(
      result,
      "concello-exemplo:pleno-xuno:evidence-auga-1",
    ),
    true,
  );
  assert.equal(
    resultMatchesReference(
      result,
      "concello-exemplo:pleno-maio:evidence-auga-1",
    ),
    false,
  );
});

test("computes retrieval, no-answer, and latency metrics without query text", () => {
  const outcomes = [
    outcome("q001", "exact_quote", false, ["o:s1:e1"], [
      result("s1", "e1"),
    ], 10),
    outcome("q002", "when", false, ["o:s1:e2"], [
      result("s1", "other"),
      result("s1", "e2"),
    ], 20),
    outcome("q003", "no_answer", true, [], [], 30),
  ];
  const metrics = computeMetrics(outcomes, "hybrid");

  assert.equal(metrics.recallAt20, 1);
  assert.equal(metrics.taskSuccessAt20, 1);
  assert.equal(metrics.meanReciprocalRank, 0.75);
  assert.ok(Math.abs(metrics.ndcgAt20 - 0.8154648768) < 1e-9);
  assert.equal(metrics.falseNoAnswerRate, 0);
  assert.equal(metrics.correctNoAnswerRate, 1);
  assert.equal(metrics.misleadingNoAnswerRate, 0);
  assert.deepEqual(metrics.latencyMs, { p50: 20, p95: 29, maximum: 30 });
  assert.equal("query" in metrics, false);
});

test("does not inflate nDCG when a topic and its transcript cite the same evidence", () => {
  const metrics = computeMetrics([
    outcome("duplicate", "exact_quote", false, ["o:s1:e1"], [
      result("s1", "e1"), result("s1", "e1"), result("s1", "e1"),
    ], 10),
  ], "hybrid");
  assert.equal(metrics.ndcgAt20, 1);
  assert.equal(metrics.recallAt20, 1);
});

function outcome(id, intent, expectedNoAnswer, references, results, latencyMs) {
  return {
    evaluationCase: {
      id,
      intent,
      expectedNoAnswer,
      relevantEvidenceRefs: references,
    },
    results,
    requestError: null,
    latencyMs,
  };
}

function result(publicSlug, evidenceSegmentId) {
  return { publicSlug, evidenceSegmentId, sourceEntityId: null };
}
