import { describe, expect, it } from "vitest";
import {
  isPublicPath,
  isSafeReturnPath,
  PUBLIC_EXACT_PATHS,
  PUBLIC_PREFIX_PATHS,
} from "./config";

describe("isPublicPath", () => {
  it("matches '/' exactly", () => {
    expect(isPublicPath("/")).toBe(true);
  });

  it("matches every public prefix", () => {
    for (const prefix of PUBLIC_PREFIX_PATHS) {
      expect(isPublicPath(prefix), `prefix root ${prefix}`).toBe(true);
      expect(isPublicPath(`${prefix}/some-deep-path`), `prefix nested ${prefix}`).toBe(true);
    }
  });

  it("does NOT match protected paths", () => {
    const protectedPaths = [
      "/dashboard",
      "/profile",
      "/rooms",
      "/sessions",
      "/account-recovery-real", // shares the /account-recovery prefix but isn't the same route
    ];
    for (const path of protectedPaths) {
      expect(isPublicPath(path), `${path} should not be public`).toBe(false);
    }
  });

  it("treats same-prefix siblings correctly", () => {
    // "/login" is public, but "/login-attacker" must NOT be.
    expect(isPublicPath("/login")).toBe(true);
    expect(isPublicPath("/login-attacker")).toBe(false);
  });

  it("does NOT match an empty path", () => {
    expect(isPublicPath("")).toBe(false);
  });

  it("does NOT match anything that happens to share a '/' prefix (no false positives)", () => {
    expect(PUBLIC_EXACT_PATHS.has("/")).toBe(true);
    // "/" is exact only; "/foo" must not match "/" via startsWith
    expect(isPublicPath("/dashboard")).toBe(false);
  });
});

describe("isSafeReturnPath", () => {
  it("accepts absolute same-origin paths", () => {
    expect(isSafeReturnPath("/")).toBe(true);
    expect(isSafeReturnPath("/dashboard?x=1")).toBe(true);
    expect(isSafeReturnPath("/profile/account")).toBe(true);
  });

  it("rejects protocol-relative URLs (open-redirect guard)", () => {
    expect(isSafeReturnPath("//evil.com/path")).toBe(false);
    expect(isSafeReturnPath("///example.com")).toBe(false);
  });

  it("rejects absolute external URLs", () => {
    expect(isSafeReturnPath("https://evil.com")).toBe(false);
    expect(isSafeReturnPath("http://localhost:3000/dashboard")).toBe(false);
  });

  it("rejects empty / relative paths", () => {
    expect(isSafeReturnPath("")).toBe(false);
    expect(isSafeReturnPath("dashboard")).toBe(false);
    expect(isSafeReturnPath("../etc")).toBe(false);
  });
});
