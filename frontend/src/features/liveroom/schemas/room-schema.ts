import { z } from "zod";

export const createRoomFormSchema = z.object({
  title: z
    .string()
    .min(1, { message: "validation.liveroom.title.required" })
    .max(200, { message: "validation.liveroom.title.maxlength" }),
  description: z
    .string()
    .max(1000, { message: "validation.liveroom.description.maxlength" })
    .optional()
    .or(z.literal("")),
  mode: z.enum(["PUBLIC", "PRIVATE"], {
    message: "validation.liveroom.mode.required",
  }),
  maxParticipants: z
    .number({ message: "validation.liveroom.capacity.range" })
    .int()
    .min(2, { message: "validation.liveroom.capacity.range" })
    .max(5, { message: "validation.liveroom.capacity.range" }),
});

export type CreateRoomFormValues = z.infer<typeof createRoomFormSchema>;

export const declineRequestFormSchema = z.object({
  reason: z
    .string()
    .max(500, { message: "validation.liveroom.askMessage.maxlength" })
    .optional()
    .or(z.literal("")),
});

export type DeclineRequestFormValues = z.infer<typeof declineRequestFormSchema>;
