import { z } from "zod";
import { PASSWORD_COMPLEXITY_REGEX } from "../hooks/password-validators";

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(2, "fullNameRequired")
      .max(100, "fullNameLength"),
    email: z
      .string()
      .trim()
      .min(1, "emailRequired")
      .email("invalidEmail")
      .max(255, "emailLength"),
    password: z
      .string()
      .min(8, "minPassword")
      .max(128, "maxPassword")
      .regex(PASSWORD_COMPLEXITY_REGEX, "passwordComplexity"),
    confirmPassword: z.string().min(1, "confirmPasswordRequired"),
    captchaToken: z.string().optional(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "passwordMismatch",
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;