import { z } from "zod";

export const LANGUAGE_CODE_VALUES = [
  "vi-VN",
  "en-US",
  "en-GB",
] as const;

export type LanguageCode = (typeof LANGUAGE_CODE_VALUES)[number];

export const ttsFormSchema = z.object({
  name: z
    .string()
    .min(1, { message: "validation.name.required" })
    .max(100, { message: "validation.name.maxlength" }),
  text: z
    .string()
    .min(1, { message: "validation.text.required" })
    .max(2000, { message: "validation.text.maxlength" }),
  // Mirrors the server's whitelist instead of a loose locale shape, so an unsupported language is
  // rejected in the form rather than after a round trip.
  languageCode: z.enum(LANGUAGE_CODE_VALUES, {
    message: "validation.languagecode.pattern",
  }),
  /** Empty string means "let the provider choose" — the server treats a missing voice the same way. */
  voiceName: z.string().optional(),
});

export type TtsFormValues = z.infer<typeof ttsFormSchema>;
