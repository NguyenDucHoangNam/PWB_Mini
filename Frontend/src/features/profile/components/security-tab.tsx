"use client";

import { Loader2, Lock, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
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
    <div role="tabpanel" className="p-6 sm:p-8">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200">
          <Lock className="size-5" aria-hidden="true" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-50">
            {labels.oldPassword.replace("*", "").trim()}
          </h2>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{labels.newPassword}</p>
        </div>
      </div>

      {isOauthOnly ? (
        <div className="flex flex-col items-center gap-4 rounded-xl border border-dashed border-neutral-200 bg-neutral-50 py-10 text-center dark:border-neutral-800 dark:bg-neutral-900/40">
          <ShieldCheck className="size-8 text-neutral-400" aria-hidden="true" />
          <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
            {oauthOnlyMessage}
          </p>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4">
          {passwordError && (
            <div className="rounded-lg border border-red-200/60 bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:border-red-900/40 dark:bg-red-950/30 dark:text-red-300">
              {passwordError}
            </div>
          )}

          <FieldGroup label={labels.oldPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={currentPassword}
              onChange={(e) => onChangeCurrentPassword(e.target.value)}
              placeholder={labels.oldPlaceholder}
            />
          </FieldGroup>

          <FieldGroup label={labels.newPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={newPassword}
              onChange={(e) => onChangeNewPassword(e.target.value)}
              placeholder={labels.newPlaceholder}
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
            />
          </FieldGroup>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              disabled={isSubmitting || isRateLimited}
              className="min-w-[140px]"
            >
              {isSubmitting ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="size-4 animate-spin" />
                  {labels.submitting}
                </span>
              ) : isRateLimited ? (
                labels.retryText
              ) : (
                labels.submit
              )}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
