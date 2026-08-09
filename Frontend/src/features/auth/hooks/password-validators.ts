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
    return { level: 0, label: "", percentage: 0, colorClass: "bg-slate-400 dark:bg-slate-500" };
  }

  const hasLower = /[a-z]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);

  if (password.length < PASSWORD_MIN_LENGTH) {
    return { level: 1, label: "", percentage: 20, colorClass: "bg-rose-600 dark:bg-rose-400" };
  }

  if (hasLower && hasUpper && hasDigit && hasSpecial) {
    return {
      level: 4,
      label: "",
      percentage: 100,
      colorClass: "bg-emerald-700 dark:bg-emerald-400",
    };
  }

  if (hasLower && hasUpper && hasDigit) {
    return {
      level: 3,
      label: "",
      percentage: 80,
      colorClass: "bg-indigo-600 dark:bg-indigo-400",
    };
  }

  return {
    level: 2,
    label: "",
    percentage: 50,
    colorClass: "bg-amber-600 dark:bg-amber-400",
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

export function isPasswordValid(password: string): boolean {
  return evaluatePasswordRules(password).every((rule) => rule.passed);
}

export const passwordValidators = [
  { id: "minLength", isValid: (p: string) => p.length >= PASSWORD_MIN_LENGTH },
  { id: "upper", isValid: (p: string) => /[A-Z]/.test(p) },
  { id: "lower", isValid: (p: string) => /[a-z]/.test(p) },
  { id: "digit", isValid: (p: string) => /\d/.test(p) },
  { id: "special", isValid: (p: string) => /[^A-Za-z0-9]/.test(p) },
  { id: "noWhitespace", isValid: (p: string) => !/\s/.test(p) },
];