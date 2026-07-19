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
});

export type UploadSongFormValues = z.infer<typeof uploadSongFormSchema>;

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