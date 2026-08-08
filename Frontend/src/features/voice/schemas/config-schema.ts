import { z } from "zod";

export const configFormSchema = z.object({
  voiceTagId: z
    .string()
    .min(1, { message: "validation.song.required" }),
  intervalSeconds: z
    .number()
    .min(5, { message: "validation.interval.min" })
    .max(600, { message: "validation.interval.max" }),
  volumePercentage: z
    .number()
    .min(0, { message: "validation.volume.min" })
    .max(100, { message: "validation.volume.max" }),
  duckingPercentage: z
    .number()
    .min(0, { message: "validation.ducking.min" })
    .max(100, { message: "validation.ducking.max" }),
  startOffsetSeconds: z
    .number()
    .min(0, { message: "validation.startOffset.min" }),
  enabled: z.boolean(),
});

export type ConfigFormValues = z.infer<typeof configFormSchema>;

export const DEFAULT_CONFIG_VALUES: ConfigFormValues = {
  voiceTagId: "",
  intervalSeconds: 30,
  volumePercentage: 80,
  duckingPercentage: 50,
  startOffsetSeconds: 0,
  enabled: true,
};

