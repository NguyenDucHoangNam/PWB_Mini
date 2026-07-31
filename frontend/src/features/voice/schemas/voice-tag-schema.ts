import { z } from "zod";

export const ttsFormSchema = z.object({
  name: z
    .string()
    .min(1, { message: "validation.name.required" })
    .max(128, { message: "validation.name.maxlength" }),
  text: z
    .string()
    .min(1, { message: "validation.text.required" })
    .max(4000, { message: "validation.text.maxlength" }),
  languageCode: z
    .string()
    .min(1, { message: "validation.languagecode.required" })
    .regex(/^[a-z]{2}-[A-Z]{2}$/, {
      message: "validation.languagecode.pattern",
    }),
});

export type TtsFormValues = z.infer<typeof ttsFormSchema>;

export const LANGUAGE_CODE_VALUES = [
  "en-US",
  "en-GB",
  "vi-VN",
  "ja-JP",
  "ko-KR",
  "zh-CN",
  "es-ES",
  "fr-FR",
  "de-DE",
] as const;

export type LanguageCode = (typeof LANGUAGE_CODE_VALUES)[number];