import { describe, expect, it } from "vitest";
import { nextIdempotencyKey, nextRequestId, shortId } from "./format";

describe("shortId", () => {
  it("returns placeholder for missing value", () => {
    expect(shortId("")).toBe("-");
  });

  it("truncates long values", () => {
    expect(shortId("1234567890abcdef")).toBe("123456...abcdef");
  });
});

describe("request and idempotency ids", () => {
  it("generates request ids in UUID-like format", () => {
    const requestId = nextRequestId();
    expect(requestId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it("generates idempotency keys with prefix", () => {
    const key = nextIdempotencyKey("earn");
    expect(key).toMatch(/^earn-[0-9a-f-]{36}$/);
  });
});
