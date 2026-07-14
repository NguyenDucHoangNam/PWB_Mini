export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;

export const PASSWORD_COMPLEXITY_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]+$/;

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isPasswordComplexityValid(password: string): boolean {
  return (
    password.length >= PASSWORD_MIN_LENGTH &&
    password.length <= PASSWORD_MAX_LENGTH &&
    PASSWORD_COMPLEXITY_REGEX.test(password)
  );
}

export interface PasswordStrengthInput {
  level: number;
  label: string;
  percentage: number;
  colorClass: string;
}

export function calculatePasswordStrength(password: string): PasswordStrengthInput {
  if (!password) {
    return { level: 0, label: "", percentage: 0, colorClass: "bg-neutral-200" };
  }

  const hasLower = /[a-z]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSpecial = /[@$!%*?&]/.test(password);

  if (password.length < PASSWORD_MIN_LENGTH) {
    return { level: 1, label: "", percentage: 20, colorClass: "bg-neutral-300" };
  }

  if (hasLower && hasUpper && hasDigit && hasSpecial) {
    return {
      level: 4,
      label: "",
      percentage: 100,
      colorClass: "bg-neutral-800 dark:bg-neutral-200",
    };
  }

  if (hasLower && hasUpper && hasDigit) {
    return {
      level: 3,
      label: "",
      percentage: 80,
      colorClass: "bg-neutral-600 dark:bg-neutral-400",
    };
  }

  return {
    level: 2,
    label: "",
    percentage: 50,
    colorClass: "bg-neutral-400 dark:bg-neutral-500",
  };
}