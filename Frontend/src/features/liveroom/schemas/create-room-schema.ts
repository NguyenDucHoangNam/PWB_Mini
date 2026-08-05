import { z } from "zod";
import {
  ROOM_MAX_CAPACITY,
  ROOM_MAX_GRACE_SECONDS,
  ROOM_MIN_CAPACITY,
  ROOM_MIN_GRACE_SECONDS,
} from "../types";

export const ROOM_NAME_MAX_LENGTH = 100;

export const createRoomSchema = z.object({
  roomName: z
    .string()
    .trim()
    .min(1, { message: "validation.name.required" })
    .max(ROOM_NAME_MAX_LENGTH, { message: "validation.name.maxlength" }),
  maxParticipants: z
    .number()
    .int()
    .min(ROOM_MIN_CAPACITY, { message: "liveroom.create.capacityInvalid" })
    .max(ROOM_MAX_CAPACITY, { message: "liveroom.create.capacityInvalid" }),
  ownerGraceSeconds: z
    .number()
    .int()
    .min(ROOM_MIN_GRACE_SECONDS, { message: "liveroom.create.graceInvalid" })
    .max(ROOM_MAX_GRACE_SECONDS, { message: "liveroom.create.graceInvalid" }),
});

export type CreateRoomFormValues = z.infer<typeof createRoomSchema>;