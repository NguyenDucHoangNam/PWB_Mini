"use client";

import { useTranslations } from "next-intl";
import {
  evaluatePasswordRules,
  type PasswordRule,
} from "../hooks/password-validators";

interface PasswordRulesProps {
  password: string;
}

export function PasswordRules({ password }: PasswordRulesProps) {
  const t = useTranslations("auth.register.passwordRules");
  const rules = evaluatePasswordRules(password);
  const showRules = password.length > 0;

  if (!showRules) return null;

  return (
    <div
      role="list"
      aria-label={t("title")}
      className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2 font-sans"
    >
      {rules.map((rule) => (
        <PasswordRuleItem key={rule.id} rule={rule} />
      ))}
    </div>
  );
}

interface PasswordRuleItemProps {
  rule: PasswordRule;
}

function PasswordRuleItem({ rule }: PasswordRuleItemProps) {
  const t = useTranslations("auth.register.passwordRules");
  const label = t(rule.id);
  const passed = rule.passed;

  return (
    <div
      role="listitem"
      aria-live="polite"
      className={`flex items-center gap-1.5 text-xs font-semibold transition-colors ${
        passed ? "text-emerald-600 dark:text-emerald-400" : "text-neutral-400 dark:text-neutral-500"
      }`}
    >
      <span
        aria-hidden="true"
        className={`inline-flex size-3.5 items-center justify-center rounded-full text-[10px] font-bold ${
          passed ? "bg-emerald-100 dark:bg-emerald-950/40" : "bg-neutral-100 dark:bg-neutral-800"
        }`}
      >
        {passed ? "✓" : "○"}
      </span>
      <span>{label}</span>
    </div>
  );
}