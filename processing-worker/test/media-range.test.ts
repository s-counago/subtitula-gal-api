import { describe, expect, it } from "vitest";
import { parseRange } from "../src/index";

describe("public media range parsing", () => {
  it("supports complete, bounded, open, and suffix ranges", () => {
    expect(parseRange(null, 1_000)).toEqual({
      offset: 0,
      length: 1_000,
      partial: false,
    });
    expect(parseRange("bytes=100-199", 1_000)).toEqual({
      offset: 100,
      length: 100,
      partial: true,
    });
    expect(parseRange("bytes=900-", 1_000)).toEqual({
      offset: 900,
      length: 100,
      partial: true,
    });
    expect(parseRange("bytes=-25", 1_000)).toEqual({
      offset: 975,
      length: 25,
      partial: true,
    });
  });

  it("rejects malformed and unsatisfiable ranges", () => {
    expect(() => parseRange("items=0-1", 100)).toThrow("range_invalid");
    expect(() => parseRange("bytes=100-101", 100)).toThrow("range_invalid");
    expect(() => parseRange("bytes=50-20", 100)).toThrow("range_invalid");
  });
});
