import { z } from "zod";
import { PASSWORD_COMPLEXITY_REGEX } from "../hooks/password-validators";

export const completeProfileSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, "usernameRequired")
    .max(50, "usernameLength")
    .regex(/^[a-zA-Z0-9_-]+$/, "usernameInvalid"),
  fullName: z
    .string()
    .trim()
    .max(100, "fullNameLength")
    .optional()
    .or(z.literal("")),
  newPassword: z
    .string()
    .max(128, "maxPassword")
    .optional()
    .or(z.literal(""))
    .refine(
      (val) => !val || (val.length >= 8 && PASSWORD_COMPLEXITY_REGEX.test(val)),
      "passwordComplexity",
    ),
});

export type CompleteProfileFormValues = z.infer<typeof completeProfileSchema>;
