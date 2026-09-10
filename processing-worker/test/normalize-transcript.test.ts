import { describe, expect, it } from "vitest";
import { normalizeTranscript } from "../src/schemas/normalize-transcript";

describe("speaker-aware transcript normalization", () => {
  it("preserves evidence timing and splits on speaker and punctuation", () => {
    const normalized = normalizeTranscript({
      language_code: "glg",
      words: [
        word("Falamos ", 0, 0.5, "speaker_0"),
        word("do orzamento.", 0.5, 1.2, "speaker_0", -1.2),
        word("E ", 1.3, 1.5, "speaker_1"),
        word("das tubaxes.", 1.5, 2.2, "speaker_1"),
      ],
    });

    expect(normalized.schemaVersion).toBe("1.0.0");
    expect(normalized.languageCode).toBe("glg");
    expect(normalized.durationMs).toBe(2_200);
    expect(normalized.speakers).toEqual([
      { providerLabel: "speaker_0" },
      { providerLabel: "speaker_1" },
    ]);
    expect(normalized.segments).toHaveLength(2);
    expect(normalized.segments[0]).toMatchObject({
      sequence: 0,
      startMs: 0,
      endMs: 1_200,
      speakerProviderLabel: "speaker_0",
      text: "Falamos do orzamento.",
      signals: {
        speakerChanged: false,
        hasLowLogProbability: true,
      },
    });
    expect(normalized.segments[1].signals.speakerChanged).toBe(true);
  });

  it("bounds long segments and surfaces review signals", () => {
    const normalized = normalizeTranscript({
      language_code: "glg",
      words: [
        word("María " + "a".repeat(480), 0, 0.2, "speaker_0"),
        {
          text: "[risas]",
          type: "audio_event",
          speaker_id: "speaker_0",
        },
        word("b".repeat(30), 31, 31.2, "speaker_0"),
      ],
    });

    expect(normalized.segments).toHaveLength(2);
    expect(normalized.segments[0].text.length).toBeLessThanOrEqual(500);
    expect(normalized.segments[0].signals).toMatchObject({
      hasAudioEvent: true,
      hasMissingTiming: true,
      properNameCandidates: ["María"],
    });
    expect(normalized.segments[1]).toMatchObject({
      startMs: 31_000,
      endMs: 31_200,
    });
  });

  it("rejects an artifact without usable word evidence", () => {
    expect(() => normalizeTranscript({ words: [] })).toThrow("transcript_empty");
    expect(() => normalizeTranscript({ text: "only a blob" }))
      .toThrow("transcript_words_missing");
  });

  it("does not turn routine agenda and sentence starters into name warnings", () => {
    const lines = [
      "Primer punto: biblioteca.", "Segundo punto: transporte.",
      "Terceiro punto: preguntas.", "Tras el debate, se aprueba.",
      "Una intervención pregunta.", "Esta es una prueba para Subtitula.",
      "Non se adopta ningún acordo.", "Antes solicitarase un informe.",
      "Propónse abrir a sala.", "Levántase a sesión.",
    ];
    const result = normalizeTranscript({ words: lines.map((text, i) => word(text, i, i + 1, "speaker_0")) });
    expect(result.segments.map((segment) => segment.signals.properNameCandidates))
      .toEqual([[], [], [], [], [], ["Subtitula"], [], [], [], []]);
  });

  it("preserves accented names at sentence starts and within evidence", () => {
    const result = normalizeTranscript({ words: [word("Ángela falou con María en Ézaro.", 0, 1, "speaker_0")] });
    expect(result.segments[0].signals.properNameCandidates).toEqual(["Ángela", "María", "Ézaro"]);
  });
});

function word(
  text: string,
  start: number,
  end: number,
  speaker: string,
  logprob = -0.01,
) {
  return {
    text,
    start,
    end,
    speaker_id: speaker,
    type: "word",
    logprob,
  };
}
