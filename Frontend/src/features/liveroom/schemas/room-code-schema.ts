import { z } from "zod";
import { ROOM_CODE_LENGTH } from "../types";

export const roomCodeSchema = z.object({
  roomCode: z
    .string()
    .trim()
    .toUpperCase()
    .regex(new RegExp(`^[A-Z0-9]{${ROOM_CODE_LENGTH}}$`), {
      message: "liveroom.join.codeInvalid",
    }),
});

export type RoomCodeFormValues = z.infer<typeof roomCodeSchema>;