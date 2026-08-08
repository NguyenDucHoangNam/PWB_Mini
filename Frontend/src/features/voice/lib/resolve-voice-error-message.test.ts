import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import { resolveVoiceErrorMessage } from "./resolve-voice-error-message";
import en from "../../../../messages/en.json";
import vi from "../../../../messages/vi.json";

/**
 * The audio module's codes were renamed `VOICE_0xx` -> `AUDIO_0xx` on the backend, and this map was not
 * updated for a long time. Nothing appeared to break: `resolveVoiceErrorMessage` falls back to the
 * server's own localized message, so users kept seeing sensible text while every translation on this
 * side was dead. That is the failure mode these tests exist for — a silent one, invisible from the UI.
 */

const AUDIO_ERROR_CODE_SOURCE = resolve(
  __dirname,
  "../../../../../Backend/modules/audio/src/main/java/com/pwb/audio/application/exception/AudioErrorCode.java",
);

/** Reads the codes straight out of the enum, so the list cannot drift from the backend. */
function backendAudioErrorCodes(): string[] {
  const source = readFileSync(AUDIO_ERROR_CODE_SOURCE, "utf8");
  return [...source.matchAll(/"(AUDIO_\d{3})"/g)].map((match) => match[1]);
}

type Dict = Record<string, unknown>;

function lookup(messages: Dict, dottedKey: string): string | undefined {
  const value = dottedKey
    .split(".")
    .reduce<unknown>((node, part) => (node as Dict | undefined)?.[part], messages);
  return typeof value === "string" ? value : undefined;
}

const asApiError = (code: string) => Object.assign(new Error("boom"), { code });

describe("audio error codes reach a translation", () => {
  it("finds a real backend enum to read", () => {
    // Guards the two tests below: a moved or renamed enum would make them pass over an empty list.
    expect(backendAudioErrorCodes().length).toBeGreaterThan(20);
  });

  it("maps every code the backend can emit", () => {
    const unmapped = backendAudioErrorCodes().filter(
      (code) => !resolveErrorI18nKey(asApiError(code)),
    );
    expect(unmapped).toEqual([]);
  });

  it.each([
    ["en", en as Dict],
    ["vi", vi as Dict],
  ])("resolves every mapped key to %s text", (_locale, messages) => {
    const missing = backendAudioErrorCodes()
      .map((code) => resolveErrorI18nKey(asApiError(code)))
      .filter((key): key is string => Boolean(key))
      .filter((key) => lookup(messages, key) === undefined);
    expect(missing).toEqual([]);
  });
});

describe("resolveVoiceErrorMessage", () => {
  const tErrors = (key: string) => `errors:${key}`;
  const tCommon = (key: string) => `common:${key}`;

  it("translates a known audio code", () => {
    expect(resolveVoiceErrorMessage(asApiError("AUDIO_027"), tErrors, tCommon)).toBe(
      "errors:voiceTagTooLong",
    );
  });

  it("turns the PRO guard's 403 into an upgrade prompt", () => {
    // The backend answers the generic IAM code here; without this the user is told "access denied" on a
    // screen they reached expecting the feature to work.
    expect(resolveVoiceErrorMessage(asApiError("IAM_ACCESS_001"), tErrors, tCommon)).toBe(
      "errors:proOnly",
    );
  });

  it("falls back to the server message for an unknown code", () => {
    const error = Object.assign(new Error("x"), {
      code: "SOMETHING_NEW",
      message: "Server said this",
    });
    expect(resolveVoiceErrorMessage(error, tErrors, tCommon)).toBe("Server said this");
  });

  it("falls back to the common message when there is nothing else", () => {
    expect(resolveVoiceErrorMessage(null, tErrors, tCommon)).toBe("common:error");
  });
});
