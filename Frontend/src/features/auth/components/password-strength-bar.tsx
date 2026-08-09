"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { PasswordStrengthResult } from "../hooks/use-password-strength";

interface PasswordStrengthBarProps {
  strength: PasswordStrengthResult;
  className?: string;
}

export function PasswordStrengthBar({ strength, className }: PasswordStrengthBarProps) {
  const t = useTranslations("auth.reset");
  const { level, percentage, colorClass } = strength;

  if (level === 0) return null;

  const getLabel = () => {
    switch (level) {
      case 1:
        return t("strengthWeak");
      case 2:
        return t("strengthMedium");
      case 3:
        return t("strengthStrong");
      case 4:
        return t("strengthVeryStrong");
      default:
        return "";
    }
  };

  return (
    <div className={cn("flex flex-col justify-center gap-1 font-sans", className)}>
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium text-slate-600 dark:text-slate-400">{t("strengthTitle")}</span>
        <span className="font-bold text-slate-900 dark:text-slate-100">{getLabel()}</span>
      </div>
      <div className="neu-pressed-sm h-2 w-full overflow-hidden rounded-full border-none">
        <div
          className={`h-full rounded-full transition-all duration-300 ease-out motion-reduce:transition-none ${colorClass}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
