import { useMemo } from "react";
import {
  calculatePasswordStrength,
  type PasswordStrengthInput,
} from "./password-validators";

export type PasswordStrengthResult = PasswordStrengthInput;

export function usePasswordStrength(password: string): PasswordStrengthResult {
  return useMemo<PasswordStrengthResult>(() => calculatePasswordStrength(password), [password]);
}