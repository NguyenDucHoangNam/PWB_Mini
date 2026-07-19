import { z } from "zod";

export const configFormSchema = z.object({
  voiceTagId: z
    .string()
    .min(1, { message: "validation.song.required" }),
  intervalSeconds: z
    .number()
    .min(1, { message: "validation.interval.required" })
    .max(60, { message: "validation.interval.max" }),
  volumePercentage: z
    .number()
    .min(0, { message: "validation.volume.min" })
    .max(100, { message: "validation.volume.max" }),
  fadeInDurationMs: z
    .number()
    .min(0, { message: "validation.fadeIn.min" })
    .max(5000, { message: "validation.fadeIn.max" }),
  fadeOutDurationMs: z
    .number()
    .min(0, { message: "validation.fadeOut.min" })
    .max(5000, { message: "validation.fadeOut.max" }),
  startOffsetSeconds: z
    .number()
    .min(0, { message: "validation.startOffset.min" })
    .max(300, { message: "validation.startOffset.max" }),
  enabled: z.boolean(),
});

export type ConfigFormValues = z.infer<typeof configFormSchema>;

export const DEFAULT_CONFIG_VALUES: ConfigFormValues = {
  voiceTagId: "",
  intervalSeconds: 30,
  volumePercentage: 80,
  fadeInDurationMs: 500,
  fadeOutDurationMs: 500,
  startOffsetSeconds: 0,
  enabled: true,
};
