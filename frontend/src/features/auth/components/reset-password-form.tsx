"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useResetPassword } from "../api/reset-password";
import { usePasswordStrength } from "../hooks/use-password-strength";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH } from "../hooks/password-validators";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";

export function ResetPasswordForm() {
  const t = useTranslations("auth.reset");
  const router = useRouter();
  const searchParams = useSearchParams();

  const token = searchParams.get("token") || "";

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);

  const { mutate: resetMutate, isPending } = useResetPassword();
  const strength = usePasswordStrength(newPassword);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!newPassword || !confirmPassword) {
      setError(t("fillAll"));
      return;
    }

    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      setError(t("minLen"));
      return;
    }

    if (newPassword.length > PASSWORD_MAX_LENGTH) {
      setError(t("maxLen") || "Password must be at most 128 characters");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError(t("notMatch"));
      return;
    }

    setError(null);

    resetMutate(
      { data: { token, newPassword } },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("successToast"));
            setIsSuccess(true);
            setTimeout(() => {
              router.push("/login");
            }, 5000);
          } else {
            setError(response.message || t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("invalidTokenError"));
          toast.error(t("errorToast"));
        }),
      },
    );
  };

  if (!token) {
    return (
      <div className="flex flex-col gap-6 text-center font-sans">
        <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900 text-neutral-800 dark:text-neutral-200">
          <svg className="size-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("invalidLinkTitle")}
          </h1>
          <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
            {t("invalidLinkDesc")}
          </p>
        </div>

        <div className="flex flex-col gap-3 mt-2 w-full">
          <Link href="/forgot-password">
            <Button variant="default" size="lg" className="w-full h-11 text-sm font-semibold">
              {t("resendLinkBtn")}
            </Button>
          </Link>
          <Link
            href="/login"
            className="text-center text-sm text-neutral-500 dark:text-neutral-400 font-semibold hover:underline"
          >
            {t("backToLogin")}
          </Link>
        </div>
      </div>
    );
  }

  if (isSuccess) {
    return (
      <div className="flex flex-col gap-6 text-center font-sans">
        <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900 text-neutral-800 dark:text-neutral-200">
          <svg className="size-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("successTitle")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("successDesc")}</p>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("desc")}</p>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          {error}
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="newPassword">{t("newPasswordLabel")}</Label>
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
        <Label htmlFor="confirmPassword">{t("confirmPasswordLabel")}</Label>
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

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending}
        className="w-full justify-center h-10 font-bold"
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
        ) : (
          t("submitBtn")
        )}
      </Button>
    </form>
  );
}
