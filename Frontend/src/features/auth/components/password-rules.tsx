"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import {
  evaluatePasswordRules,
  type PasswordRule,
} from "../hooks/password-validators";

interface PasswordRulesProps {
  password: string;
  className?: string;
}

export function PasswordRules({ password, className }: PasswordRulesProps) {
  const t = useTranslations("auth.register.passwordRules");
  const rules = evaluatePasswordRules(password);
  const showRules = password.length > 0;

  if (!showRules) return null;

  return (
    <div
      role="list"
      aria-label={t("title")}
      className={cn("grid grid-cols-2 gap-x-3 gap-y-1 font-sans", className)}
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
        passed ? "text-emerald-800 dark:text-emerald-400" : "text-slate-600 dark:text-slate-400"
      }`}
    >
      <span
        aria-hidden="true"
        className={`inline-flex size-3.5 items-center justify-center rounded-full text-[10px] font-bold ${
          passed ? "neu-raised-sm" : "neu-pressed-sm"
        }`}
      >
        {passed ? "✓" : "○"}
      </span>
      <span>{label}</span>
    </div>
  );
}