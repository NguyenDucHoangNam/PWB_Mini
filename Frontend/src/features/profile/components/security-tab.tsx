"use client";

import { Loader2, Lock, ShieldCheck } from "lucide-react";
import { PasswordInput } from "@/features/auth/components/password-input";
import { PasswordStrengthBar } from "@/features/auth/components/password-strength-bar";
import { PasswordRules } from "@/features/auth/components/password-rules";
import type { usePasswordStrength } from "@/features/auth/hooks/use-password-strength";
import { FieldGroup } from "./personal-tab";

export interface SecurityTabProps {
  isOauthOnly: boolean;
  oauthOnlyMessage: string;
  passwordError: string | null;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  strength: ReturnType<typeof usePasswordStrength>;
  isSubmitting: boolean;
  isRateLimited: boolean;
  retryRemainingSeconds: number;
  onChangeCurrentPassword: (value: string) => void;
  onChangeNewPassword: (value: string) => void;
  onChangeConfirmPassword: (value: string) => void;
  onBlurConfirmPassword: () => void;
  onSubmit: (e: React.FormEvent) => void;
  labels: {
    oldPassword: string;
    oldPlaceholder: string;
    newPassword: string;
    newPlaceholder: string;
    confirmPassword: string;
    confirmPlaceholder: string;
    submit: string;
    submitting: string;
    retryText: string;
  };
}

export function SecurityTab({
  isOauthOnly,
  oauthOnlyMessage,
  passwordError,
  currentPassword,
  newPassword,
  confirmPassword,
  strength,
  isSubmitting,
  isRateLimited,
  onChangeCurrentPassword,
  onChangeNewPassword,
  onChangeConfirmPassword,
  onBlurConfirmPassword,
  onSubmit,
  labels,
}: SecurityTabProps) {
  return (
    <div role="tabpanel" className="neu-raised space-y-6 rounded-3xl bg-[#e0e5ec] p-6 sm:p-8 dark:bg-[#1e222b]">
      <div className="mb-6 flex items-center gap-3">
        <div className="neu-pressed flex size-11 items-center justify-center rounded-2xl text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]">
          <Lock className="size-5" aria-hidden="true" />
        </div>
        <div>
          <h2 className="text-base font-bold text-slate-900 dark:text-slate-50">
            {labels.oldPassword.replace("*", "").trim()}
          </h2>
          <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{labels.newPassword}</p>
        </div>
      </div>

      {isOauthOnly ? (
        <div className="neu-pressed flex flex-col items-center gap-4 rounded-3xl bg-[#e0e5ec] p-8 text-center dark:bg-[#1e222b]">
          <ShieldCheck className="size-9 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
          <p className="max-w-md text-sm font-medium text-slate-600 dark:text-slate-300">
            {oauthOnlyMessage}
          </p>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-5">
          {passwordError && (
            <div className="neu-pressed rounded-2xl bg-[#e0e5ec] p-4 text-xs font-semibold text-rose-600 dark:bg-[#1e222b] dark:text-rose-400">
              {passwordError}
            </div>
          )}

          <FieldGroup label={labels.oldPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={currentPassword}
              onChange={(e) => onChangeCurrentPassword(e.target.value)}
              placeholder={labels.oldPlaceholder}
              className="neu-input rounded-2xl bg-[#e0e5ec] px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:bg-[#1e222b] dark:text-slate-100 border-none h-12"
            />
          </FieldGroup>

          <FieldGroup label={labels.newPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={newPassword}
              onChange={(e) => onChangeNewPassword(e.target.value)}
              placeholder={labels.newPlaceholder}
              className="neu-input rounded-2xl bg-[#e0e5ec] px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:bg-[#1e222b] dark:text-slate-100 border-none h-12"
            />
            <PasswordStrengthBar strength={strength} />
            <PasswordRules password={newPassword} />
          </FieldGroup>

          <FieldGroup label={labels.confirmPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={confirmPassword}
              onChange={(e) => onChangeConfirmPassword(e.target.value)}
              onBlur={onBlurConfirmPassword}
              placeholder={labels.confirmPlaceholder}
              className="neu-input rounded-2xl bg-[#e0e5ec] px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:bg-[#1e222b] dark:text-slate-100 border-none h-12"
            />
          </FieldGroup>

          <div className="flex justify-end pt-3">
            <button
              type="submit"
              disabled={isSubmitting || isRateLimited}
              className="neu-button-primary flex min-w-[160px] items-center justify-center gap-2 rounded-2xl bg-indigo-600 px-6 py-3 text-sm font-bold text-white shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  <span>{labels.submitting}</span>
                </>
              ) : isRateLimited ? (
                labels.retryText
              ) : (
                labels.submit
              )}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
