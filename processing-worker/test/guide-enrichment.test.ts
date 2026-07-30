import { describe, expect, it } from "vitest";
import {
  alignAgenda,
  buildGuideWindows,
  type GuideWindow,
} from "../src/schemas/align-agenda";
import {
  materializeGuide,
  validateGuideWindow,
} from "../src/schemas/guide-extraction";
import type {
  EnrichmentAgendaItem,
  EnrichmentContext,
  EnrichmentSegment,
} from "../src/types";

describe("evidence-bound guide enrichment", () => {
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
      startSegmentId: "s2",
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
    jobVersion: 1,
    projectVersion: 2,
    segments,
    agendaItems: [],
  };
}
