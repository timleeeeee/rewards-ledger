import { describe, expect, it } from "vitest";
import { shortId } from "./format";

describe("shortId", () => {
  it("returns placeholder for missing value", () => {
    expect(shortId("")).toBe("-");
  });

  it("truncates long values", () => {
    expect(shortId("1234567890abcdef")).toBe("123456...abcdef");
  });
});
