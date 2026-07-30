import type { ProcessingEnv } from "../types";

export async function embedTexts(
  env: ProcessingEnv,
  texts: string[],
  expectedDimensions: number,
): Promise<number[][]> {
  if (texts.length === 0 || texts.some((text) => !text.trim())) {
    throw new Error("embedding_input_invalid");
  }
  const output = await env.AI.run(
    env.EMBEDDING_MODEL_ID as keyof AiModels,
    { text: texts } as never,
  );
  return parseEmbeddingOutput(output, texts.length, expectedDimensions);
}

export function parseEmbeddingOutput(
  value: unknown,
  expectedCount: number,
  expectedDimensions: number,
): number[][] {
  if (!value || typeof value !== "object" || !("data" in value)) {
    throw new Error("embedding_output_invalid");
  }
  const raw = (value as { data?: unknown }).data;
  if (!Array.isArray(raw) || raw.length !== expectedCount) {
    throw new Error("embedding_output_invalid");
  }
  const vectors = raw.map((item) => {
    const candidate = Array.isArray(item)
      ? item
      : item && typeof item === "object" && "embedding" in item
        ? (item as { embedding?: unknown }).embedding
        : null;
    if (!Array.isArray(candidate) || candidate.length !== expectedDimensions) {
      throw new Error("embedding_dimension_mismatch");
    }
    return candidate.map((component) => {
      if (
        typeof component !== "number"
        || !Number.isFinite(component)
        || Math.abs(component) > 1_000
      ) {
        throw new Error("embedding_output_invalid");
      }
      return component;
    });
  });
  return vectors;
}
