"use client";

import { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useVerifyOtp, useResendOtp } from "../api/verify-otp";
import { useAuthStore } from "../stores/use-auth-store";
import { OtpInput } from "./otp-input";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";

export function OtpForm() {
  const t = useTranslations("auth.otp");
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((state) => state.setAuth);

  const email = searchParams.get("email") || "";
  const [otpCode, setOtpCode] = useState("");
  const [error, setError] = useState<string | null>(null);

  // Countdown timers
  const [otpExpiry, setOtpExpiry] = useState(300); // 5 minutes expiration
  const [resendCooldown, setResendCooldown] = useState(60); // 60s resend cooldown

  const { mutate: verifyMutate, isPending: isVerifying } = useVerifyOtp();
  const { mutate: resendMutate, isPending: isResending } = useResendOtp();

  // Handle countdowns
  useEffect(() => {
    const interval = setInterval(() => {
      setOtpExpiry((prev) => (prev > 0 ? prev - 1 : 0));
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  const maskEmail = (emailStr: string) => {
    if (!emailStr) return "";
    const [local, domain] = emailStr.split("@");
    if (!local || !domain) return emailStr;
    if (local.length <= 2) return `${local}**@${domain}`;
    return `${local.substring(0, 2)}******@${domain}`;
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const handleVerify = (e: React.FormEvent) => {
    e.preventDefault();
    if (otpCode.length !== 6) {
      setError(t("lengthError"));
      return;
    }
    if (otpExpiry === 0) {
      setError(t("expiredError"));
      return;
    }

    setError(null);

    verifyMutate(
      {
        data: { email, otp: otpCode },
      },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            setAuth(response.data.accessToken, response.data.user);
            toast.success(t("successToast"));
            router.push("/dashboard");
          } else {
            setError(response.message || t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("verificationFailed"));
          toast.error(t("errorToast"));
        }),
      },
    );
  };

  const handleResend = () => {
    if (resendCooldown > 0) return;

    setError(null);
    resendMutate(
      {
        data: { email },
      },
      {
        onSuccess: () => {
          toast.success(t("resendSuccess"));
          setOtpExpiry(300);
          setResendCooldown(60);
        },
        onError: asApiError((err) => {
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("resendFailed"));
          toast.error(t("resendFailedToast"));
        }),
      },
    );
  };

  return (
    <form onSubmit={handleVerify} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("otpTitle")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("emailSentText")}{" "}
          <strong className="text-neutral-800 dark:text-neutral-200">{maskEmail(email)}</strong>
        </p>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200"
        >
          {error}
        </div>
      )}

      {/* 6 Digit inputs */}
      <div className="flex flex-col gap-4">
        <OtpInput disabled={isVerifying || otpExpiry === 0} onChange={setOtpCode} />

        {/* Countdown timer */}
        <div className="text-center text-xs font-semibold text-neutral-500">
          {otpExpiry > 0 ? (
            <span className="flex items-center justify-center gap-1.5">
              {t("timeRemaining")}{" "}
              <span className="text-black dark:text-white font-mono text-sm">
                {formatTime(otpExpiry)}
              </span>
            </span>
          ) : (
            <span className="text-black dark:text-white font-semibold">{t("expiredText")}</span>
          )}
        </div>
      </div>

      {/* Verify Button */}
      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isVerifying || otpCode.length !== 6 || otpExpiry === 0}
        className="w-full justify-center h-10 font-bold"
      >
        {isVerifying ? (
          <span className="flex items-center gap-2">
            <svg
              className="animate-spin size-4 text-white dark:text-black"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
            {t("verifying")}
          </span>
        ) : (
          t("verifyBtn")
        )}
      </Button>

      {/* Resend Link */}
      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400">
        {t("notReceivedText")}{" "}
        {resendCooldown > 0 ? (
          <span className="text-neutral-400 font-semibold cursor-not-allowed">
            {t("resendCooldownText", { seconds: resendCooldown })}
          </span>
        ) : (
          <button
            type="button"
            disabled={isResending}
            onClick={handleResend}
            className="font-semibold text-black dark:text-white hover:underline focus:outline-none"
          >
            {isResending ? t("resending") : t("resendLink")}
          </button>
        )}
      </div>
    </form>
  );
}
