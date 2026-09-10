import { describe, expect, it, vi } from "vitest";
import {
  alignAgenda,
  buildGuideWindows,
  type GuideWindow,
} from "../src/schemas/align-agenda";
import {
  materializeGuide,
  validateGuideWindow,
  generateGuideWindow,
} from "../src/schemas/guide-extraction";
import type {
  EnrichmentAgendaItem,
  EnrichmentContext,
  EnrichmentSegment,
  ProcessingEnv,
} from "../src/types";

describe("evidence-bound guide enrichment", () => {
  it("accepts the hosted GLM chat-completion contract and still validates unique citations", async () => {
    const run = vi.fn(async () => ({
      choices: [{ finish_reason: "stop", message: {
        role: "assistant", content: JSON.stringify({topics: [topic(["s0"])]}),
      }}],
      usage: {prompt_tokens: 100, completion_tokens: 25},
    }));
    const env = {
      AI: {run}, GUIDE_MODEL_ID: "@cf/zai-org/glm-4.7-flash",
      ESTIMATED_CHARS_PER_TOKEN: "3",
    } as unknown as ProcessingEnv;
    const context = contextWith([segment("s0", 0, "Debate sobre a biblioteca.")]);
    const output = await generateGuideWindow(env, context, guideWindow(["s0"]));
    expect(output.validated.topics).toHaveLength(1);
    expect(output.usage).toEqual({inputTokens: 100, outputTokens: 25, estimated: false});
    expect(JSON.stringify(run.mock.calls)).not.toContain("uniqueItems");
    expect(run).toHaveBeenCalledWith("@cf/zai-org/glm-4.7-flash", expect.objectContaining({
      reasoning_effort: null,
      response_format: expect.objectContaining({
        type: "json_schema",
        json_schema: expect.objectContaining({ name: "cited_session_guide", strict: true,
          schema: expect.objectContaining({ type: "object" }),
        }),
      }),
    }));
    expect(() => validateGuideWindow({topics: [topic(["s0", "s0"])]},
      guideWindow(["s0"]), context.segments, null)).toThrow("guide_evidence_invalid");
  });

  it("rejects incomplete hosted model output even if the content happens to parse", async () => {
    const env = {
      AI: {run: async () => ({choices: [{finish_reason: "length", message: {
        content: JSON.stringify({topics: [topic(["s0"])]}),
      }}]})}, GUIDE_MODEL_ID: "@cf/zai-org/glm-4.7-flash",
      ESTIMATED_CHARS_PER_TOKEN: "3",
    } as unknown as ProcessingEnv;
    await expect(generateGuideWindow(env,
      contextWith([segment("s0", 0, "Debate sobre a biblioteca.")]),
      guideWindow(["s0"]))).rejects.toThrow("guide_generation_incomplete");
  });

  it("finds monotonic agenda anchors and surfaces only weak boundaries", () => {
    const segments = [
      segment("s0", 0, "A presidencia abre a sesión."),
      segment("s1", 1, "Debatemos o orzamento municipal e os seus créditos."),
      segment("s2", 2, "Continúa o debate económico."),
      segment("s3", 3, "A rede de auga e as tubaxes precisan investimento."),
    ];
    const agenda: EnrichmentAgendaItem[] = [
      agendaItem("a0", 0, "Orzamento municipal"),
      agendaItem("a1", 1, "Rede de auga e tubaxes"),
    ];

    const alignments = alignAgenda(agenda, segments);

    expect(alignments).toHaveLength(2);
    expect(alignments[0]).toMatchObject({
      agendaItemId: "a0",
      startSegmentId: "s1",
      state: "automatic",
      requiresHumanCheck: false,
    });
    expect(alignments[1]).toMatchObject({
      agendaItemId: "a1",
      startSegmentId: "s3",
      state: "automatic",
      requiresHumanCheck: false,
    });
    expect(segments.findIndex((value) => value.id === alignments[0].startSegmentId))
      .toBeLessThan(segments.findIndex(
        (value) => value.id === alignments[1].startSegmentId,
      ));
  });

  it("bounds every model window while retaining all evidence", () => {
    const segments = Array.from({ length: 9 }, (_, index) =>
      segment(`s${index}`, index, "texto ".repeat(20))
    );
    const windows = buildGuideWindows(segments, [], 350, 3);

    expect(windows.length).toBeGreaterThan(1);
    expect(windows.flatMap((value) => value.segmentIds)).toEqual(
      segments.map((value) => value.id),
    );
    expect(windows.every((value) => value.segmentIds.length <= 3)).toBe(true);
  });

  it("rejects fabricated citations and omits decisions without outcome evidence", () => {
    const segments = [
      segment("s0", 0, "Falouse dunha proposta, sen votación nin acordo."),
    ];
    const window = guideWindow(["s0"]);

    expect(() => validateGuideWindow({
      topics: [topic(["fabricated"])],
    }, window, segments, null)).toThrow("guide_evidence_invalid");

    const validated = validateGuideWindow({
      topics: [topic(["s0"], {
        decisions: [{
          neutralDescription: "Aprobouse a proposta.",
          motion: null,
          result: null,
          evidenceSegmentIds: ["s0"],
        }],
      })],
    }, window, segments, null);

    expect(validated.topics[0].decisions).toEqual([]);
  });

  it("keeps explicit outcomes, downgrades unsupported stance labels, and is deterministic", async () => {
    const segments = [
      {
        ...segment("s0", 0, "A proposta queda aprobada por unanimidade."),
        speakerId: "speaker-1",
      },
    ];
    const window = guideWindow(["s0"]);
    const validated = validateGuideWindow({
      topics: [topic(["s0"], {
        contributions: [{
          speakerId: "speaker-1",
          kind: "support",
          neutralSummary: "Presentou a proposta.",
          evidenceSegmentIds: ["s0"],
        }],
        decisions: [{
          neutralDescription: "A proposta foi aprobada por unanimidade.",
          motion: "Aprobar a proposta",
          result: "Aprobada",
          evidenceSegmentIds: ["s0"],
        }],
      })],
    }, window, segments, null);
    expect(validated.topics[0].contributions[0]).toMatchObject({
      kind: "other",
      explicitClassification: false,
    });
    expect(validated.topics[0].decisions).toHaveLength(1);

    const context = contextWith(segments);
    const metadata = {
      schemaVersion: "1.0.0",
      model: "test-model",
      promptVersion: "guide-v1",
      rawArtifactKey: "pending",
      jobVersion: 1,
      projectVersion: 2,
      alignments: [],
      providerUsage: {
        provider: "cloudflare-workers-ai" as const,
        model: "test-model",
        windowCount: 1,
        inputTokens: 100,
        outputTokens: 25,
        estimated: false,
        inputMicroUsdPerMillionTokens: 60_000,
        outputMicroUsdPerMillionTokens: 400_000,
        pricingVersion: "test",
      },
      costMicrounits: 16,
    };
    const left = await materializeGuide(context, [validated], metadata);
    const right = await materializeGuide(context, [validated], metadata);
    expect(right.contentHash).toBe(left.contentHash);
    expect(right.topics[0].id).toBe(left.topics[0].id);
    const variants = [
      await materializeGuide({ ...context, transcriptRevisionId: "revision-2" }, [validated], metadata),
      await materializeGuide(context, [validated], { ...metadata, promptVersion: "guide-v2" }),
      await materializeGuide(context, [{ ...validated, topics: [{ ...validated.topics[0], title: "Revised topic" }] }], metadata),
    ];
    for (const variant of variants) {
      expect(variant.topics[0].id).not.toBe(left.topics[0].id);
      expect(variant.topics[0].contributions[0].id).not.toBe(left.topics[0].contributions[0].id);
      expect(variant.topics[0].decisions[0].id).not.toBe(left.topics[0].decisions[0].id);
    }
  });

  it.each([
    "Non se adopta ningún acordo sobre esta proposta.",
    "No se adopta ningún acuerdo sobre esta propuesta.",
  ])("omits a fabricated approval when the evidence says: %s", (text) => {
    const validated = validateGuideWindow({
      topics: [topic(["s0"], {
        decisions: [{
          neutralDescription: "Aprobouse abrir a biblioteca.",
          motion: "Abrir a biblioteca",
          result: "Aprobada",
          evidenceSegmentIds: ["s0"],
        }],
      })],
    }, guideWindow(["s0"]), [segment("s0", 0, text)], null);
    expect(validated.topics[0].decisions).toEqual([]);
  });
});

function segment(id: string, sequence: number, text: string): EnrichmentSegment {
  return {
    id,
    sequence,
    startMs: sequence * 1_000,
    endMs: sequence * 1_000 + 900,
    speakerId: null,
    speakerLabel: "Persoa non identificada",
    text,
  };
}

function agendaItem(
  id: string,
  ordinal: number,
  title: string,
): EnrichmentAgendaItem {
  return {
    id,
    ordinal,
    externalIdentifier: null,
    title,
    description: null,
  };
}

function guideWindow(ids: string[]): GuideWindow {
  return {
    id: "window-000",
    agendaItemId: null,
    startSequence: 0,
    endSequence: ids.length - 1,
    segmentIds: ids,
  };
}

function topic(
  evidenceSegmentIds: string[],
  overrides: Record<string, unknown> = {},
) {
  return {
    title: "Tema",
    neutralSummary: "Resumo neutral con evidencia.",
    aliases: [],
    evidenceSegmentIds,
    contributions: [],
    decisions: [],
    ...overrides,
  };
}

function contextWith(segments: EnrichmentSegment[]): EnrichmentContext {
  return {
    jobId: "job",
    projectId: "project",
    transcriptRevisionId: "revision",
    transcriptContentHash: "a".repeat(64),
    languageCode: "glg",
    projectName: "Pleno",
    sessionDate: "2026-07-29",
    sessionBody: "Pleno municipal",
    automaticAgendaEnabled: true,
    structuredGuideEnabled: true,
    jobVersion: 1,
    projectVersion: 2,
    segments,
    agendaItems: [],
  };
}
