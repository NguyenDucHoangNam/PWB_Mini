export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

export const PASSWORD_COMPLEXITY_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S+$/;

export const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

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
  const hasSpecial = /[^A-Za-z0-9]/.test(password);

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

export interface PasswordRule {
  id: "minLength" | "upper" | "lower" | "digit" | "special" | "noWhitespace";
  passed: boolean;
}

export function evaluatePasswordRules(password: string): PasswordRule[] {
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);
  const hasWhitespace = /\s/.test(password);

  return [
    { id: "minLength", passed: password.length >= PASSWORD_MIN_LENGTH },
    { id: "upper", passed: hasUpper },
    { id: "lower", passed: hasLower },
    { id: "digit", passed: hasDigit },
    { id: "special", passed: hasSpecial },
    { id: "noWhitespace", passed: !hasWhitespace },
  ];
}