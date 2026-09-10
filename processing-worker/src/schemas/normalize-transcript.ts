import type {
  ElevenLabsTranscription,
  ElevenLabsWord,
} from "../providers/elevenlabs";

type NormalizedWord = {
  sourceWordId: string;
  text: string;
  startMs: number | null;
  endMs: number | null;
  type: string;
  speakerProviderLabel: string | null;
  logProbability: number | null;
};

type NormalizedSegment = {
  sequence: number;
  startMs: number;
  endMs: number;
  speakerProviderLabel: string;
  text: string;
  wordTimings: NormalizedWord[];
  signals: {
    speakerChanged: boolean;
    hasLowLogProbability: boolean;
    hasAudioEvent: boolean;
    hasMissingTiming: boolean;
    properNameCandidates: string[];
  };
};

export type NormalizedTranscript = {
  schemaVersion: "1.0.0";
  languageCode: string;
  durationMs: number;
  speakers: Array<{ providerLabel: string }>;
  segments: NormalizedSegment[];
};

const MAX_SEGMENT_MS = 30_000;
const MAX_SEGMENT_CHARS = 500;
const LOW_LOGPROBABILITY = -1;
// Capitalization at the beginning of a sentence is not itself a name signal.
// Keep unfamiliar tokens (including names such as María) and names elsewhere.
const COMMON_SENTENCE_STARTERS = new Set([
  "esta", "este", "estas", "estos", "estes", "esa", "ese", "esas", "eses", "esos",
  "una", "uno", "unos", "unas", "unha", "unhas", "uns", "tras", "pero", "porén",
  "primeiro", "primeira", "primer", "primero", "primera", "segundo", "segunda",
  "terceiro", "terceira", "tercer", "tercero", "tercera",
]);

export function normalizeTranscript(
  transcription: ElevenLabsTranscription,
): NormalizedTranscript {
  if (!Array.isArray(transcription.words)) {
    throw new Error("transcript_words_missing");
  }
  const words = transcription.words.map(normalizeWord);
  const contentWords = words.filter((word) => word.text.length > 0);
  if (contentWords.length === 0) throw new Error("transcript_empty");

  const segments: NormalizedSegment[] = [];
  let pending: NormalizedWord[] = [];
  let pendingSpeaker = "speaker_unknown";
  let priorSpeaker: string | null = null;

  const flush = () => {
    if (pending.length === 0) return;
    const timed = pending.filter(
      (word): word is NormalizedWord & { startMs: number; endMs: number } =>
        word.startMs !== null && word.endMs !== null,
    );
    if (timed.length === 0) throw new Error("segment_timing_missing");
    const text = pending.map((word) => word.text).join("").trim();
    if (!text) {
      pending = [];
      return;
    }
    const properNames = [...new Set(
      [...text.matchAll(/(?<!\p{L})\p{Lu}\p{Ll}{2,}(?!\p{L})/gu)]
        .filter((match) => {
          const sentenceStart = /(?:^|[.!?…])\s*[¿¡"'«»]*\s*$/.test(text.slice(0, match.index));
          return !sentenceStart || !COMMON_SENTENCE_STARTERS.has(match[0].toLowerCase());
        })
        .map((match) => match[0]),
    )].slice(0, 20);
    segments.push({
      sequence: segments.length,
      startMs: timed[0].startMs,
      endMs: timed[timed.length - 1].endMs,
      speakerProviderLabel: pendingSpeaker,
      text,
      wordTimings: pending,
      signals: {
        speakerChanged: priorSpeaker !== null && priorSpeaker !== pendingSpeaker,
        hasLowLogProbability: pending.some(
          (word) => word.logProbability !== null && word.logProbability < LOW_LOGPROBABILITY,
        ),
        hasAudioEvent: pending.some((word) => word.type === "audio_event"),
        hasMissingTiming: pending.some((word) => word.startMs === null || word.endMs === null),
        properNameCandidates: properNames,
      },
    });
    priorSpeaker = pendingSpeaker;
    pending = [];
  };

  for (const word of contentWords) {
    const speaker = word.speakerProviderLabel ?? pendingSpeaker;
    const firstTimed = pending.find((candidate) => candidate.startMs !== null);
    const crossesSpeaker = pending.length > 0 && speaker !== pendingSpeaker;
    const crossesDuration = pending.length > 0
      && firstTimed?.startMs !== null
      && firstTimed?.startMs !== undefined
      && word.endMs !== null
      && word.endMs - firstTimed.startMs > MAX_SEGMENT_MS;
    const crossesLength = pending.reduce((total, candidate) => total + candidate.text.length, 0)
      + word.text.length > MAX_SEGMENT_CHARS;
    if (crossesSpeaker || crossesDuration || crossesLength) flush();
    if (pending.length === 0) pendingSpeaker = speaker || "speaker_unknown";
    pending.push(word);
    if (word.type === "word" && /[.!?…]\s*$/.test(word.text)) flush();
  }
  flush();
  if (segments.length === 0) throw new Error("transcript_segments_missing");

  const speakers = [...new Set(segments.map((segment) => segment.speakerProviderLabel))]
    .sort()
    .map((providerLabel) => ({ providerLabel }));
  const durationMs = Math.max(...segments.map((segment) => segment.endMs));
  return {
    schemaVersion: "1.0.0",
    languageCode: typeof transcription.language_code === "string"
      ? transcription.language_code
      : "glg",
    durationMs,
    speakers,
    segments,
  };
}

function normalizeWord(value: unknown, index: number): NormalizedWord {
  if (typeof value !== "object" || value === null) {
    throw new Error("transcript_word_invalid");
  }
  const word = value as ElevenLabsWord;
  const text = typeof word.text === "string" ? word.text : "";
  const startMs = secondsToMs(word.start);
  const endMs = secondsToMs(word.end);
  if (startMs !== null && endMs !== null && endMs < startMs) {
    throw new Error("transcript_word_timing_invalid");
  }
  return {
    sourceWordId: `w${index}`,
    text,
    startMs,
    endMs,
    type: typeof word.type === "string" ? word.type : "word",
    speakerProviderLabel: typeof word.speaker_id === "string" ? word.speaker_id : null,
    logProbability: typeof word.logprob === "number" && Number.isFinite(word.logprob)
      ? word.logprob
      : null,
  };
}

function secondsToMs(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0
    ? Math.round(value * 1000)
    : null;
}
