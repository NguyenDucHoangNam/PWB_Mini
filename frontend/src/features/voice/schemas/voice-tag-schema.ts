import { z } from "zod";

export const ttsFormSchema = z.object({
  name: z
    .string()
    .min(1, { message: "validation.name.required" })
    .max(100, { message: "validation.name.maxlength" }),
  text: z
    .string()
    .min(1, { message: "validation.text.required" })
    .max(2000, { message: "validation.text.maxlength" }),
  languageCode: z
    .string()
    .min(1, { message: "validation.languagecode.required" })
    .regex(/^[a-z]{2}-[A-Z]{2}$/, {
      message: "validation.languagecode.pattern",
    }),
});

export type TtsFormValues = z.infer<typeof ttsFormSchema>;

export const LANGUAGE_CODE_VALUES = [
  "vi-VN",
  "en-US",
  "en-GB",
] as const;

export type LanguageCode = (typeof LANGUAGE_CODE_VALUES)[number];