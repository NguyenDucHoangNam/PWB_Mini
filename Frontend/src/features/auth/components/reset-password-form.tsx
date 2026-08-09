"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useResetPassword } from "../api/reset-password";
import { usePasswordStrength } from "../hooks/use-password-strength";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import { resetPasswordSchema, type ResetPasswordFormValues } from "../schemas/reset-password-schema";
import {
  NEU_DANGER_TEXT,
  NEU_ERROR_TEXT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";

export function ResetPasswordForm() {
  const t = useTranslations("auth.reset");
  const router = useRouter();
  const searchParams = useSearchParams();

  const token = searchParams.get("token") || "";

  const [isSuccess, setIsSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({
    resolver: standardSchemaResolver(resetPasswordSchema),
    mode: "onSubmit",
    reValidateMode: "onBlur",
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  const newPassword = watch("newPassword");
  // Only the root slot carries server-side failures; field errors come from the schema.
  const formError = errors.root?.message ?? null;

  const { mutate: resetMutate, isPending } = useResetPassword();
  const strength = usePasswordStrength(newPassword);

  const onSubmit = handleSubmit((values) => {
    clearErrors("root");
    resetMutate(
      { data: { token, newPassword: values.newPassword } },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("successToast"));
            setIsSuccess(true);
            setTimeout(() => {
              router.push("/login");
            }, 5000);
          } else {
            setError("root", { message: response.message || t("errorToast") });
          }
        },
        onError: asApiError((err) => {
          setError("root", { message: err.message || t("invalidTokenError") });
          toast.error(t("errorToast"));
        }),
      },
    );
  });

  if (!token) {
    return (
      <div className="flex flex-col gap-6 text-center font-sans">
        <div className="neu-pressed mx-auto flex size-16 items-center justify-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
          <svg className="size-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
            {t("invalidLinkTitle")}
          </h1>
          <p className={`text-sm font-medium leading-relaxed ${NEU_TEXT_MUTED}`}>
            {t("invalidLinkDesc")}
          </p>
        </div>

        <div className="flex flex-col gap-3 mt-2 w-full">
          <Link href="/forgot-password">
            <NeuButton variant="primary" size="lg" className="w-full">
              {t("resendLinkBtn")}
            </NeuButton>
          </Link>
          <Link
            href="/login"
            className={`text-center text-sm font-semibold hover:underline ${NEU_TEXT_MUTED}`}
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
        <div className="neu-pressed mx-auto flex size-16 items-center justify-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
          <svg className="size-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
            {t("successTitle")}
          </h1>
          <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("successDesc")}</p>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
          {t("title")}
        </h1>
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("desc")}</p>
      </div>

      {formError && (
        <div
          role="alert"
          aria-live="assertive"
          className={`neu-pressed rounded-2xl border-none p-3.5 text-xs font-semibold ${NEU_DANGER_TEXT}`}
        >
          {formError}
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="newPassword" className={NEU_LABEL}>{t("newPasswordLabel")}</Label>
        <PasswordInput
          id="newPassword"
          disabled={isPending}
          {...register("newPassword")}
          placeholder={t("newPasswordPlaceholder")}
          aria-invalid={!!errors.newPassword}
        />
        {errors.newPassword?.message && (
          <span className={NEU_ERROR_TEXT}>
            {t(errors.newPassword.message as never)}
          </span>
        )}
        <PasswordStrengthBar strength={strength} />
        <PasswordRules password={newPassword} />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="confirmPassword" className={NEU_LABEL}>{t("confirmPasswordLabel")}</Label>
        <PasswordInput
          id="confirmPassword"
          disabled={isPending}
          {...register("confirmPassword")}
          placeholder={t("confirmPasswordPlaceholder")}
          aria-invalid={!!errors.confirmPassword}
        />
        {errors.confirmPassword?.message && (
          <span className={NEU_ERROR_TEXT}>
            {t(errors.confirmPassword.message as never)}
          </span>
        )}
      </div>

      <NeuButton
        type="submit"
        variant="primary"
        size="lg"
        disabled={isPending}
        className="w-full"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <svg className="size-4 animate-spin text-white motion-reduce:animate-none" fill="none" viewBox="0 0 24 24">
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
      </NeuButton>
    </form>
  );
}
