import type {
  EnrichmentAgendaItem,
  EnrichmentSegment,
} from "../types";

export const AGENDA_ALIGNMENT_VERSION = "monotonic-anchors-v2";

export type AgendaAlignmentDraft = {
  agendaItemId: string;
  occurrence: number;
  startSegmentId: string;
  endSegmentId: string;
  signals: {
    confidence: number;
    anchorSequence: number;
    anchorTerms: string[];
    fallback: boolean;
  };
  state: "automatic" | "unresolved";
  requiresHumanCheck: boolean;
  algorithmVersion: string;
  revisited: false;
};

export type GuideWindow = {
  id: string;
  agendaItemId: string | null;
  startSequence: number;
  endSequence: number;
  segmentIds: string[];
};

type AnchorCandidate = {
  index: number;
  score: number;
  terms: string[];
  fallback: boolean;
};

const stopWords = new Set([
  "a", "ao", "aos", "as", "da", "das", "de", "do", "dos", "e", "en",
  "na", "nas", "no", "nos", "o", "os", "para", "por", "que", "un", "unha",
  "y", "el", "la", "las", "los", "del", "una", "sobre",
]);

export function alignAgenda(
  agendaItems: EnrichmentAgendaItem[],
  segments: EnrichmentSegment[],
): AgendaAlignmentDraft[] {
  if (agendaItems.length === 0 || segments.length === 0) return [];
  const segmentTokens = segments.map((segment, index) =>
    tokens(
      `${segments[index - 1]?.text ?? ""} ${segment.text} `
      + `${segments[index + 1]?.text ?? ""}`,
    )
  );
  const candidates = agendaItems.map((item, agendaIndex) =>
    anchorCandidates(item, agendaIndex, agendaItems.length, segments, segmentTokens)
  );
  const selected = monotonicPath(candidates, segments.length >= agendaItems.length);

  return agendaItems.map((item, index) => {
    const anchor = selected[index];
    const nextIndex = selected[index + 1]?.index ?? segments.length;
    const endIndex = Math.max(anchor.index, nextIndex - 1);
    const confidence = round(anchor.score);
    return {
      agendaItemId: item.id,
      occurrence: 0,
      startSegmentId: segments[anchor.index].id,
      endSegmentId: segments[Math.min(endIndex, segments.length - 1)].id,
      signals: {
        confidence,
        anchorSequence: segments[anchor.index].sequence,
        anchorTerms: anchor.terms.slice(0, 8),
        fallback: anchor.fallback,
      },
      state: confidence < 0.12 ? "unresolved" : "automatic",
      requiresHumanCheck: confidence < 0.34 || anchor.fallback,
      algorithmVersion: AGENDA_ALIGNMENT_VERSION,
      revisited: false,
    };
  });
}

export function buildGuideWindows(
  segments: EnrichmentSegment[],
  alignments: AgendaAlignmentDraft[],
  maximumCharacters = 24_000,
  maximumSegments = 80,
): GuideWindow[] {
  if (segments.length === 0) return [];
  const indexById = new Map(segments.map((segment, index) => [segment.id, index]));
  const ranges: Array<{
    agendaItemId: string | null;
    start: number;
    end: number;
  }> = [];
  const ordered = alignments
    .map((value) => ({
      value,
      start: indexById.get(value.startSegmentId),
      end: indexById.get(value.endSegmentId),
    }))
    .filter((value): value is typeof value & { start: number; end: number } =>
      value.start != null && value.end != null
    )
    .sort((left, right) => left.start - right.start);
  let cursor = 0;
  for (const alignment of ordered) {
    if (alignment.start > cursor) {
      ranges.push({ agendaItemId: null, start: cursor, end: alignment.start - 1 });
    }
    ranges.push({
      agendaItemId: alignment.value.agendaItemId,
      start: alignment.start,
      end: Math.max(alignment.start, alignment.end),
    });
    cursor = Math.max(cursor, alignment.end + 1);
  }
  if (cursor < segments.length) {
    ranges.push({ agendaItemId: null, start: cursor, end: segments.length - 1 });
  }
  if (ranges.length === 0) {
    ranges.push({ agendaItemId: null, start: 0, end: segments.length - 1 });
  }

  const windows: GuideWindow[] = [];
  for (const range of ranges) {
    let start = range.start;
    while (start <= range.end) {
      let end = start;
      let characters = 0;
      while (end <= range.end && end - start < maximumSegments) {
        const next = segments[end].text.length + 120;
        if (end > start && characters + next > maximumCharacters) break;
        characters += next;
        end++;
      }
      const inclusiveEnd = Math.max(start, end - 1);
      windows.push({
        id: `window-${windows.length.toString().padStart(3, "0")}`,
        agendaItemId: range.agendaItemId,
        startSequence: segments[start].sequence,
        endSequence: segments[inclusiveEnd].sequence,
        segmentIds: segments.slice(start, inclusiveEnd + 1).map((value) => value.id),
      });
      start = inclusiveEnd + 1;
    }
  }
  return windows;
}

function anchorCandidates(
  item: EnrichmentAgendaItem,
  agendaIndex: number,
  agendaCount: number,
  segments: EnrichmentSegment[],
  segmentTokens: Array<Set<string>>,
): AnchorCandidate[] {
  const itemTerms = tokens(
    `${item.externalIdentifier ?? ""} ${item.title} ${item.description ?? ""}`,
  );
  const scored = segments.map((segment, index) => {
    const overlap = [...itemTerms].filter((term) => segmentTokens[index].has(term));
    const ownTerms = tokens(segment.text);
    const direct = [...itemTerms].filter((term) => ownTerms.has(term));
    // Neighbouring evidence helps fragmented titles, but must not place the
    // boundary in a preceding sentence that never mentions this agenda item.
    const base = itemTerms.size === 0 ? 0
      : (0.85 * direct.length + 0.15 * overlap.length) / itemTerms.size;
    const exact = normalize(segment.text).includes(normalize(item.title)) ? 0.35 : 0;
    return {
      index,
      score: Math.min(1, base + exact),
      terms: direct,
      fallback: false,
    };
  });
  const best = scored
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .slice(0, 12);
  const proportional = agendaCount <= 1
    ? 0
    : Math.round((segments.length - 1) * agendaIndex / (agendaCount - 1));
  if (!best.some((value) => value.index === proportional)) {
    best.push({
      index: proportional,
      score: 0,
      terms: [],
      fallback: true,
    });
  }
  return best.sort((left, right) => left.index - right.index);
}

function monotonicPath(
  candidates: AnchorCandidate[][],
  requireIncrease: boolean,
): AnchorCandidate[] {
  const scores: number[][] = candidates.map((row) => row.map(() => -Infinity));
  const previous: number[][] = candidates.map((row) => row.map(() => -1));
  for (let index = 0; index < candidates[0].length; index++) {
    scores[0][index] = candidates[0][index].score;
  }
  for (let agenda = 1; agenda < candidates.length; agenda++) {
    for (let current = 0; current < candidates[agenda].length; current++) {
      const currentCandidate = candidates[agenda][current];
      for (let prior = 0; prior < candidates[agenda - 1].length; prior++) {
        const priorCandidate = candidates[agenda - 1][prior];
        const valid = requireIncrease
          ? priorCandidate.index < currentCandidate.index
          : priorCandidate.index <= currentCandidate.index;
        if (!valid || !Number.isFinite(scores[agenda - 1][prior])) continue;
        const expectedGap = Math.max(1, currentCandidate.index / (agenda + 1));
        const actualGap = currentCandidate.index - priorCandidate.index;
        const spacingPenalty = Math.min(0.12, Math.abs(actualGap - expectedGap) * 0.001);
        const candidateScore = scores[agenda - 1][prior]
          + currentCandidate.score
          - spacingPenalty;
        if (candidateScore > scores[agenda][current]) {
          scores[agenda][current] = candidateScore;
          previous[agenda][current] = prior;
        }
      }
    }
  }
  let selected = scores.at(-1)!.reduce(
    (best, value, index, row) => value > row[best] ? index : best,
    0,
  );
  if (!Number.isFinite(scores.at(-1)![selected])) {
    return candidates.map((row) => row.find((value) => value.fallback) ?? row[0]);
  }
  const path = new Array<AnchorCandidate>(candidates.length);
  for (let agenda = candidates.length - 1; agenda >= 0; agenda--) {
    path[agenda] = candidates[agenda][selected];
    selected = previous[agenda][selected];
  }
  return path;
}

function tokens(value: string): Set<string> {
  return new Set(
    normalize(value)
      .split(/[^a-z0-9áéíóúüñç]+/u)
      .filter((term) => term.length >= 3 && !stopWords.has(term)),
  );
}

function normalize(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/\p{Diacritic}/gu, "")
    .toLocaleLowerCase("gl")
    .replace(/\s+/g, " ")
    .trim();
}

function round(value: number): number {
  return Math.round(value * 1_000) / 1_000;
}
