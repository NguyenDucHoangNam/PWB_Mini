import { z } from "zod";
import type { LiveRoomMode } from "../types";

const LIVEROOM_MODES = ["PUBLIC", "PRIVATE", "INVITE_ONLY", "PASSWORD"] as const satisfies readonly LiveRoomMode[];

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
    mode: z.enum(LIVEROOM_MODES, {
      message: "validation.liveroom.mode.required",
    }),
    password: z
      .string()
      .min(4, { message: "validation.liveroom.password.length" })
      .max(64, { message: "validation.liveroom.password.length" })
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
    if (data.mode === "PASSWORD" && (!data.password || data.password.length < 4)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["password"],
        message: "validation.liveroom.password.length",
      });
    }
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
  mode: z.enum(LIVEROOM_MODES).optional(),
  password: z
    .string()
    .min(4, { message: "validation.liveroom.password.length" })
    .max(64, { message: "validation.liveroom.password.length" })
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

export const joinRoomFormSchema = z.object({
  displayName: z
    .string()
    .min(1, { message: "validation.liveroom.displayname.length" })
    .max(100, { message: "validation.liveroom.displayname.length" })
    .optional()
    .or(z.literal("")),
});

export type JoinRoomFormValues = z.infer<typeof joinRoomFormSchema>;
