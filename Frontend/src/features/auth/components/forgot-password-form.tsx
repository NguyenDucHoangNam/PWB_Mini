"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { useForgotPassword } from "../api/forgot-password";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import {
  NEU_ACCENT_TEXT,
  NEU_DANGER_TEXT,
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";

export function ForgotPasswordForm() {
  const t = useTranslations("auth.forgot");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const retryCountdown = useRetryCountdown();

  const { mutate: forgotMutate, isPending } = useForgotPassword();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!email) {
      setError(t("requiredEmail"));
      return;
    }

    if (!/\S+@\S+\.\S+/.test(email)) {
      setError(t("invalidEmail"));
      return;
    }

    setError(null);

    forgotMutate(
      { data: { email } },
      {
        onSuccess: () => {
          toast.success(t("successToast"));
          setIsSubmitted(true);
        },
        onError: asApiError((err) => {
          if (err.status === 429) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            const msg = err.retryAfterSeconds
              ? t("rateLimitErrorWithSeconds", { seconds: err.retryAfterSeconds })
              : t("rateLimitError");
            setError(msg);
            toast.warning(msg);
            return;
          }
          setError(err.message || t("errorToast"));
          toast.error(t("errorToastTitle"));
        }),
      },
    );
  };

  if (isSubmitted) {
    return (
      <div className="flex flex-col gap-6 text-center font-sans">
        <div className="neu-pressed mx-auto flex size-16 items-center justify-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
          <svg
            className="size-8"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M3 19v-8.93a2 2 0 01.89-1.664l8-5.333a2 2 0 012.22 0l8 5.333A2 2 0 0121 10.07V19M3 19a2 2 0 002 2h14a2 2 0 002-2M3 19l6.75-4.5M21 19l-6.75-4.5M3 10l6.75 4.5M21 10l-6.75 4.5m0 0l-2.25-1.5a2 2 0 00-2.22 0l-2.25 1.5"
            />
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
            {t("submittedTitle")}
          </h1>
          <p className={`text-sm font-medium leading-relaxed ${NEU_TEXT_MUTED}`}>
            {t("submittedDesc")}
          </p>
        </div>

        <Link href="/login" className="mt-2 w-full">
          <NeuButton variant="primary" size="lg" className="w-full">
            {t("backToLoginBtn")}
          </NeuButton>
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
          {t("title")}
        </h1>
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("infoText")}</p>
      </div>

      {error && (
        <div
          role="alert"
          className={`neu-pressed rounded-2xl border-none p-3.5 text-xs font-semibold ${NEU_DANGER_TEXT}`}
        >
          {error}
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email" className={NEU_LABEL}>{t("emailLabel")}</Label>
        <Input
          id="email"
          type="email"
          inputMode="email"
          disabled={isPending}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder={t("emailPlaceholder")}
          required
          className={`${NEU_INPUT} h-12`}
        />
      </div>

      <NeuButton
        type="submit"
        variant="primary"
        size="lg"
        disabled={isPending || retryCountdown.isActive}
        className="w-full"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <svg
              className="size-4 animate-spin text-white motion-reduce:animate-none"
              fill="none"
              viewBox="0 0 24 24"
            >
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
      </NeuButton>

      <div className={`text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
        <Link href="/login" className={`font-bold hover:underline ${NEU_ACCENT_TEXT}`}>
          &lt; {t("backToLogin")}
        </Link>
      </div>
    </form>
  );
}
