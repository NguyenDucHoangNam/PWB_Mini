"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useChangePassword } from "../api/change-password";
import { usePasswordStrength } from "../hooks/use-password-strength";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import { IamErrorCode } from "../lib/iam-error-codes";
import { changePasswordSchema, type ChangePasswordFormValues } from "../schemas/change-password-schema";

export function ChangePasswordForm() {
  const t = useTranslations("profile.changePassword");
  const router = useRouter();
  const { mutate: changePasswordMutate, isPending } = useChangePassword();

  const [isOauthOnly, setIsOauthOnly] = useState(false);
  const retryCountdown = useRetryCountdown();

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<ChangePasswordFormValues>({
    resolver: standardSchemaResolver(changePasswordSchema),
    mode: "onSubmit",
    reValidateMode: "onBlur",
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const newPassword = watch("newPassword");
  const formError = errors.root?.message ?? null;
  const strength = usePasswordStrength(newPassword);

  const onSubmit = handleSubmit((values) => {
    clearErrors("root");

    // "Same as the current password" is the one rule the schema cannot express, because it
    // compares two fields the server also checks; keeping it here gives immediate feedback.
    if (values.newPassword === values.currentPassword) {
      setError("newPassword", { message: "reuseError" });
      return;
    }

    changePasswordMutate(
      { data: { currentPassword: values.currentPassword, newPassword: values.newPassword } },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("success"));
            reset();
          } else {
            setError("root", { message: response.message || t("error") });
          }
        },
        onError: asApiError((err) => {
          if (err.code === IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD) {
            setIsOauthOnly(true);
          } else if (err.code === IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD) {
            setError("currentPassword", { message: "incorrectOld" });
          } else if (
            err.code === IamErrorCode.AUTH_PASSWORD_REUSED ||
            err.code === IamErrorCode.AUTH_PASSWORD_RECENTLY_USED
          ) {
            setError("newPassword", { message: "reuseError" });
          } else if (err.status === 429) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            setError("root", {
              message: err.retryAfterSeconds
                ? t("rateLimitErrorWithSeconds", { seconds: err.retryAfterSeconds })
                : t("error"),
            });
          } else {
            setError("root", { message: err.message || t("error") });
          }
          toast.error(t("error"));
        }),
      },
    );
  });

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
    <form onSubmit={onSubmit} className="flex flex-col gap-6 font-sans">
      <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">{t("title")}</h2>

      {formError && (
        <div
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          {formError}
        </div>
      )}

      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="currentPassword">{t("oldPassword")}</Label>
          <PasswordInput
            id="currentPassword"
            disabled={isPending}
            {...register("currentPassword")}
            placeholder={t("oldPasswordPlaceholder")}
            aria-invalid={!!errors.currentPassword}
          />
          {errors.currentPassword?.message && (
            <span className="text-xs text-red-600 dark:text-red-400 font-semibold">
              {t(errors.currentPassword.message as never)}
            </span>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="newPassword">{t("newPassword")}</Label>
          <PasswordInput
            id="newPassword"
            disabled={isPending}
            {...register("newPassword")}
            placeholder={t("newPasswordPlaceholder")}
            aria-invalid={!!errors.newPassword}
          />
          {errors.newPassword?.message && (
            <span className="text-xs text-red-600 dark:text-red-400 font-semibold">
              {t(errors.newPassword.message as never)}
            </span>
          )}
          <PasswordStrengthBar strength={strength} />
          <PasswordRules password={newPassword} />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="confirmPassword">{t("confirmPassword")}</Label>
          <PasswordInput
            id="confirmPassword"
            disabled={isPending}
            {...register("confirmPassword")}
            placeholder={t("confirmPasswordPlaceholder")}
            aria-invalid={!!errors.confirmPassword}
          />
          {errors.confirmPassword?.message && (
            <span className="text-xs text-red-600 dark:text-red-400 font-semibold">
              {t(errors.confirmPassword.message as never)}
            </span>
          )}
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
