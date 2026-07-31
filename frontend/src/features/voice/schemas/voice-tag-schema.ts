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

export const LANGUAGE_CODES = [
  { value: "en-US", label: "English (US)" },
  { value: "en-GB", label: "English (UK)" },
  { value: "vi-VN", label: "Tiếng Việt" },
  { value: "ja-JP", label: "日本語" },
  { value: "ko-KR", label: "한국어" },
  { value: "zh-CN", label: "中文 (简体)" },
  { value: "es-ES", label: "Español" },
  { value: "fr-FR", label: "Français" },
  { value: "de-DE", label: "Deutsch" },
] as const;