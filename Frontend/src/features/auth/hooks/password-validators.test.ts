import { describe, it, expect } from "vitest";
import {
  PASSWORD_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  EMAIL_REGEX,
  isPasswordComplexityValid,
  calculatePasswordStrength,
  evaluatePasswordRules,
} from "./password-validators";

describe("password-validators", () => {
  describe("constants", () => {
    it("uses 12 chars as the minimum (matches backend policy)", () => {
      expect(PASSWORD_MIN_LENGTH).toBe(12);
      expect(PASSWORD_MAX_LENGTH).toBe(128);
    });
  });

  describe("isPasswordComplexityValid", () => {
    it("accepts a fully valid password", () => {
      expect(isPasswordComplexityValid("Abcdefgh123!")).toBe(true);
      expect(isPasswordComplexityValid("StrongPass1@")).toBe(true);
    });

    it("rejects passwords shorter than 12 chars", () => {
      expect(isPasswordComplexityValid("Ab1!abcd")).toBe(false);
      expect(isPasswordComplexityValid("")).toBe(false);
    });

    it("rejects passwords longer than 128 chars", () => {
      const longPassword = "A1a!" + "x".repeat(130);
      expect(isPasswordComplexityValid(longPassword)).toBe(false);
    });

    it("rejects passwords without uppercase", () => {
      expect(isPasswordComplexityValid("abcdefgh123!")).toBe(false);
    });

    it("rejects passwords without lowercase", () => {
      expect(isPasswordComplexityValid("ABCDEFGH123!")).toBe(false);
    });

    it("rejects passwords without digit", () => {
      expect(isPasswordComplexityValid("Abcdefghijkl!")).toBe(false);
    });

    it("rejects passwords without special character", () => {
      expect(isPasswordComplexityValid("Abcdefgh1234")).toBe(false);
    });

    it("rejects passwords with whitespace", () => {
      expect(isPasswordComplexityValid("Abcdef gh123!")).toBe(false);
      expect(isPasswordComplexityValid("Abcdef\tgh123!")).toBe(false);
    });

    it("accepts any non-alphanumeric character as special (matches backend)", () => {
      expect(isPasswordComplexityValid("Abcdefgh123'")).toBe(true);
      expect(isPasswordComplexityValid("Abcdefgh123.")).toBe(true);
      expect(isPasswordComplexityValid("Abcdefgh123-")).toBe(true);
      expect(isPasswordComplexityValid("Abcdefgh123_")).toBe(true);
    });
  });

  describe("calculatePasswordStrength", () => {
    it("returns level 0 for empty password", () => {
      expect(calculatePasswordStrength("")).toEqual({
        level: 0,
        label: "",
        percentage: 0,
        colorClass: "bg-slate-400 dark:bg-slate-500",
      });
    });

    it("returns level 1 when shorter than minimum length", () => {
      const result = calculatePasswordStrength("Short1");
      expect(result.level).toBe(1);
    });

    it("returns level 4 when all complexity rules met", () => {
      const result = calculatePasswordStrength("Abcdefgh123!");
      expect(result.level).toBe(4);
      expect(result.percentage).toBe(100);
    });

    it("returns level 3 when missing special character", () => {
      const result = calculatePasswordStrength("Abcdefgh1234");
      expect(result.level).toBe(3);
    });

    it("returns level 2 when missing multiple components", () => {
      const result = calculatePasswordStrength("abcdefghijkl");
      expect(result.level).toBe(2);
    });
  });

  describe("evaluatePasswordRules", () => {
    it("returns 6 rules with their pass/fail state", () => {
      const rules = evaluatePasswordRules("Abcdefgh123!");
      expect(rules).toHaveLength(6);
      expect(rules.find((r) => r.id === "minLength")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "upper")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "lower")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "digit")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "special")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "noWhitespace")?.passed).toBe(true);
    });

    it("marks each rule independently", () => {
      const rules = evaluatePasswordRules("abcdefghijkl");
      expect(rules.find((r) => r.id === "minLength")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "upper")?.passed).toBe(false);
      expect(rules.find((r) => r.id === "lower")?.passed).toBe(true);
      expect(rules.find((r) => r.id === "digit")?.passed).toBe(false);
      expect(rules.find((r) => r.id === "special")?.passed).toBe(false);
      expect(rules.find((r) => r.id === "noWhitespace")?.passed).toBe(true);
    });

    it("flags whitespace as failing the noWhitespace rule", () => {
      const rules = evaluatePasswordRules("Abcdef gh123!");
      expect(rules.find((r) => r.id === "noWhitespace")?.passed).toBe(false);
    });
  });

  describe("EMAIL_REGEX", () => {
    it("accepts well-formed emails", () => {
      expect(EMAIL_REGEX.test("user@example.com")).toBe(true);
      expect(EMAIL_REGEX.test("user.name+tag@example.co.uk")).toBe(true);
    });

    it("rejects malformed emails", () => {
      expect(EMAIL_REGEX.test("plainstring")).toBe(false);
      expect(EMAIL_REGEX.test("@example.com")).toBe(false);
      expect(EMAIL_REGEX.test("user@")).toBe(false);
      expect(EMAIL_REGEX.test("user@example")).toBe(false);
      expect(EMAIL_REGEX.test("user@example.c")).toBe(false);
    });
  });
});