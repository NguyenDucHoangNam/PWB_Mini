import { useMemo } from "react";

export interface PasswordStrengthResult {
  level: number;
  label: string;
  percentage: number;
  colorClass: string;
}

const EMPTY_RESULT: PasswordStrengthResult = {
  level: 0,
  label: "",
  percentage: 0,
  colorClass: "bg-neutral-200",
};

export function usePasswordStrength(password: string): PasswordStrengthResult {
  return useMemo<PasswordStrengthResult>(() => {
    if (!password) return EMPTY_RESULT;

    let level = 1;
    let label = "Yếu";
    let percentage = 20;
    let colorClass = "bg-neutral-300";

    const hasLower = /[a-z]/.test(password);
    const hasUpper = /[A-Z]/.test(password);
    const hasDigit = /\d/.test(password);
    const hasSpecial = /[@$!%*?&#^+=!_\-]/.test(password);

    if (password.length >= 8) {
      const allCriteria = hasLower && hasUpper && hasDigit && hasSpecial;

      if (allCriteria) {
        if (password.length >= 12) {
          level = 4;
          label = "Rất mạnh";
          percentage = 100;
          colorClass = "bg-neutral-800 dark:bg-neutral-200";
        } else {
          level = 3;
          label = "Mạnh";
          percentage = 80;
          colorClass = "bg-neutral-600 dark:bg-neutral-400";
        }
      } else if (hasLower && hasUpper && hasDigit) {
        level = 2;
        label = "Trung bình";
        percentage = 50;
        colorClass = "bg-neutral-400 dark:bg-neutral-500";
      }
    }

    return { level, label, percentage, colorClass };
  }, [password]);
}