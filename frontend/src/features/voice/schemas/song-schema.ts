import { z } from "zod";

export const uploadSongFormSchema = z.object({
  title: z
    .string()
    .min(1, { message: "validation.title.required" })
    .max(256, { message: "validation.title.maxlength" }),
  artist: z
    .string()
    .max(256, { message: "validation.artist.maxlength" })
    .optional()
    .or(z.literal("")),
  album: z
    .string()
    .max(256, { message: "validation.album.maxlength" })
    .optional()
    .or(z.literal("")),
  attachVoiceTag: z.boolean().default(false),
  voiceTagId: z.string().optional().or(z.literal("")),
  intervalSeconds: z.number().min(1, { message: "validation.interval.min" }).default(10),
  volumePercentage: z.number().min(0).max(100).default(80),
  fadeInDurationMs: z.number().min(0).default(0),
  fadeOutDurationMs: z.number().min(0).default(0),
  startOffsetSeconds: z.number().min(0).default(0),
});

export type UploadSongFormValues = z.infer<typeof uploadSongFormSchema>;
export type UploadSongFormInput = z.input<typeof uploadSongFormSchema>;

export const updateSongFormSchema = z.object({
  title: z
    .string()
    .min(1, { message: "validation.title.required" })
    .max(256, { message: "validation.title.maxlength" }),
  artist: z
    .string()
    .max(256, { message: "validation.artist.maxlength" })
    .optional()
    .or(z.literal("")),
  album: z
    .string()
    .max(256, { message: "validation.album.maxlength" })
    .optional()
    .or(z.literal("")),
});

export type UpdateSongFormValues = z.infer<typeof updateSongFormSchema>;