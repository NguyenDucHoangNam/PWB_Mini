import { z } from "zod";

export const createRoomFormSchema = z
  .object({
    title: z
      .string()
      .min(1, { message: "validation.liveroom.title.required" })
      .max(200, { message: "validation.liveroom.title.maxlength" }),
    description: z
      .string()
      .max(1000, { message: "validation.liveroom.description.maxlength" })
      .optional()
      .or(z.literal("")),
    maxParticipants: z
      .number({ message: "validation.liveroom.capacity.range" })
      .int()
      .min(2, { message: "validation.liveroom.capacity.range" })
      .max(500, { message: "validation.liveroom.capacity.range" }),
    scheduledStartAt: z.string().optional().or(z.literal("")),
  })
  .superRefine((data, ctx) => {
    if (data.scheduledStartAt) {
      const scheduled = new Date(data.scheduledStartAt);
      if (Number.isNaN(scheduled.getTime())) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["scheduledStartAt"],
          message: "validation.liveroom.schedule.invalid",
        });
      } else if (scheduled.getTime() <= Date.now()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["scheduledStartAt"],
          message: "validation.liveroom.schedule.future",
        });
      }
    }
  });

export type CreateRoomFormValues = z.infer<typeof createRoomFormSchema>;

export const updateRoomFormSchema = z.object({
  title: z
    .string()
    .min(1, { message: "validation.liveroom.title.required" })
    .max(200, { message: "validation.liveroom.title.maxlength" })
    .optional()
    .or(z.literal("")),
  description: z
    .string()
    .max(1000, { message: "validation.liveroom.description.maxlength" })
    .optional()
    .or(z.literal("")),
  maxParticipants: z
    .number()
    .int()
    .min(2, { message: "validation.liveroom.capacity.range" })
    .max(500, { message: "validation.liveroom.capacity.range" })
    .optional(),
});

export type UpdateRoomFormValues = z.infer<typeof updateRoomFormSchema>;

export const askToJoinFormSchema = z.object({
  displayName: z
    .string()
    .max(100, { message: "validation.liveroom.displayname.length" })
    .optional()
    .or(z.literal("")),
  message: z
    .string()
    .max(500, { message: "validation.liveroom.askMessage.maxlength" })
    .optional()
    .or(z.literal("")),
});

export type AskToJoinFormValues = z.infer<typeof askToJoinFormSchema>;

export const declineRequestFormSchema = z.object({
  reason: z
    .string()
    .max(500, { message: "validation.liveroom.askMessage.maxlength" })
    .optional()
    .or(z.literal("")),
});

export type DeclineRequestFormValues = z.infer<typeof declineRequestFormSchema>;
