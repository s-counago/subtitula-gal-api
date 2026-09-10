import { describe, expect, it } from "vitest";
import { parseEmbeddingOutput } from "../src/schemas/embeddings";

describe("embedding projection validation", () => {
  it("accepts only the pinned count and dimensions", () => {
    expect(parseEmbeddingOutput(
      { data: [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]] },
      2,
      3,
    )).toEqual([[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]);

    expect(() => parseEmbeddingOutput(
      { data: [[0.1, 0.2]] },
      1,
      3,
    )).toThrow("embedding_dimension_mismatch");
  });

  it("rejects missing, non-finite, and provider-shaped surprises", () => {
    expect(() => parseEmbeddingOutput({}, 1, 3))
      .toThrow("embedding_output_invalid");
    expect(() => parseEmbeddingOutput(
      { data: [[0.1, Number.NaN, 0.3]] },
      1,
      3,
    )).toThrow("embedding_output_invalid");
    expect(() => parseEmbeddingOutput(
      { data: [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]] },
      1,
      3,
    )).toThrow("embedding_output_invalid");
  });
});
