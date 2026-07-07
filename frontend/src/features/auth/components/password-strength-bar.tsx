"use client";

import { useTranslations } from "next-intl";
import { PasswordStrengthResult } from "../hooks/use-password-strength";

interface PasswordStrengthBarProps {
  strength: PasswordStrengthResult;
}

export function PasswordStrengthBar({ strength }: PasswordStrengthBarProps) {
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
    <div className="mt-2 flex flex-col gap-1.5 font-sans">
      <div className="flex items-center justify-between text-xs">
        <span className="text-neutral-500 dark:text-neutral-400">{t("strengthTitle")}</span>
        <span className="font-semibold text-black dark:text-white">{getLabel()}</span>
      </div>
      <div className="h-1.5 w-full rounded-full bg-neutral-100 dark:bg-neutral-900 overflow-hidden">
        <div
          className={`h-full transition-all duration-300 ease-out ${colorClass}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
