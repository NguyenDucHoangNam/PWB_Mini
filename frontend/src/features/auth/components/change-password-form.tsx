"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useChangePassword } from "../api/change-password";
import { usePasswordStrength } from "../hooks/use-password-strength";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH } from "../hooks/password-validators";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";

export function ChangePasswordForm() {
  const t = useTranslations("profile.changePassword");
  const router = useRouter();
  const { mutate: changePasswordMutate, isPending } = useChangePassword();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isOauthOnly, setIsOauthOnly] = useState(false);
  const retryCountdown = useRetryCountdown();

  const strength = usePasswordStrength(newPassword);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!currentPassword || !newPassword || !confirmPassword) {
      setError(t("fillAll"));
      return;
    }

    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      setError(t("minLen"));
      return;
    }

    if (newPassword.length > PASSWORD_MAX_LENGTH) {
      setError(t("maxLen"));
      return;
    }

    if (newPassword === currentPassword) {
      setError(t("reuseError"));
      return;
    }

    if (newPassword !== confirmPassword) {
      setError(t("notMatch"));
      return;
    }

    setError(null);

    changePasswordMutate(
      { data: { currentPassword, newPassword } },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("success"));
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
          } else {
            setError(response.message || t("error"));
          }
        },
        onError: asApiError((err) => {
          const apiError = err.errors?.[0];
          if (apiError?.code === "AUTH_OAUTH_USER_NO_PASSWORD") {
            setIsOauthOnly(true);
          } else if (apiError?.code === "AUTH_INVALID_CURRENT_PASSWORD") {
            setError(t("incorrectOld"));
          } else if (apiError?.code === "AUTH_PASSWORD_REUSED") {
            setError(t("reuseError"));
          } else if (err.status === 429) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            const msg = err.retryAfterSeconds
              ? t("rateLimitErrorWithSeconds", { seconds: err.retryAfterSeconds })
              : t("error");
            setError(msg);
          } else {
            setError(err.message || t("error"));
          }
          toast.error(t("error"));
        }),
      },
    );
  };

  if (isOauthOnly) {
    return (
      <div className="flex flex-col gap-6 items-center text-center py-6 font-sans">
        <div className="flex size-14 items-center justify-center rounded-full bg-neutral-50 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 text-neutral-800 dark:text-neutral-200">
          <svg className="size-6" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12.24 10.285V14.4h6.887c-.648 2.41-2.519 4.114-5.136 4.114-3.524 0-6.386-2.862-6.386-6.386 0-3.524 2.862-6.386 6.386-6.386 1.63 0 3.116.618 4.256 1.63l3.056-3.056C19.34 2.502 16.035 1 12.24 1 6.136 1 1.18 5.956 1.18 12.06c0 6.104 4.956 11.06 11.06 11.06 6.368 0 11.06-4.475 11.06-11.06 0-.745-.074-1.463-.207-2.149H12.24z" />
          </svg>
        </div>
        <div className="flex flex-col gap-2">
          <h2 className="text-lg font-bold text-black dark:text-white">{t("title")}</h2>
          <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400 max-w-xs">
            {t("oauthOnly")}
          </p>
        </div>
        <div className="flex flex-col gap-3 w-full max-w-xs">
          <a
            href="https://myaccount.google.com/security"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm font-medium text-blue-600 hover:underline"
          >
            {t("manageInGoogle")}
          </a>
          <Button variant="ghost" onClick={() => router.back()} className="text-sm">
            {t("goBack")}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">{t("title")}</h2>

      {error && (
        <div
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          {error}
        </div>
      )}

      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="currentPassword">{t("oldPassword")}</Label>
          <PasswordInput
            id="currentPassword"
            disabled={isPending}
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            placeholder={t("oldPasswordPlaceholder")}
            required
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="newPassword">{t("newPassword")}</Label>
          <PasswordInput
            id="newPassword"
            disabled={isPending}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder={t("newPasswordPlaceholder")}
            required
          />
          <PasswordStrengthBar strength={strength} />
          <PasswordRules password={newPassword} />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="confirmPassword">{t("confirmPassword")}</Label>
          <PasswordInput
            id="confirmPassword"
            disabled={isPending}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            onBlur={() => {
              if (newPassword && confirmPassword && newPassword !== confirmPassword) {
                setError(t("notMatch"));
              } else if (newPassword && confirmPassword && newPassword === confirmPassword) {
                setError(null);
              }
            }}
            placeholder={t("confirmPasswordPlaceholder")}
            required
          />
        </div>
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || retryCountdown.isActive}
        className="w-full justify-center h-10 font-bold mt-2"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <svg className="animate-spin size-4 text-white dark:text-black" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
            {t("submitting")}
          </span>
        ) : retryCountdown.isActive ? (
          t("retryCountdownText", { seconds: retryCountdown.remaining })
        ) : (
          t("submit")
        )}
      </Button>
    </form>
  );
}
