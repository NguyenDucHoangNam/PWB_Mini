import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().trim().min(1, "fillAll").email("invalidEmail").max(255, "invalidEmail"),
  password: z.string().min(1, "fillAll"),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
