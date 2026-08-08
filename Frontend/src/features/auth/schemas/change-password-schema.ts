import { z } from "zod";
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from "../hooks/password-validators";

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "fillAll"),
    newPassword: z
      .string()
      .min(1, "fillAll")
      .min(PASSWORD_MIN_LENGTH, "minLen")
      .max(PASSWORD_MAX_LENGTH, "maxLen"),
    confirmPassword: z.string().min(1, "fillAll"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "notMatch",
  });

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
