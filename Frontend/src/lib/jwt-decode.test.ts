import { describe, it, expect } from "vitest";
import { decodeJwtExpiry } from "./jwt-decode";

function makeToken(payload: object): string {
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" }))
    .toString("base64")
    .replace(/=+$/, "");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64").replace(/=+$/, "");
  return `${header}.${body}.signature`;
}

describe("decodeJwtExpiry", () => {
  it("returns null on malformed tokens", () => {
    expect(decodeJwtExpiry("not-a-jwt")).toBeNull();
    expect(decodeJwtExpiry("a.b")).toBeNull();
    expect(decodeJwtExpiry("a.b.c.d")).toBeNull();
  });

  it("returns null when exp is missing", () => {
    expect(decodeJwtExpiry(makeToken({ sub: "1" }))).toBeNull();
  });

  it("returns epoch milliseconds for exp", () => {
    const exp = 1700000000;
    expect(decodeJwtExpiry(makeToken({ exp }))).toBe(exp * 1000);
  });
});
