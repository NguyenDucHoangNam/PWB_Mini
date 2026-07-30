"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useVerifyOtp } from "../api/verify-otp";
import { useResendOtp } from "../api/resend-otp";
import { useAuthStore } from "../stores/use-auth-store";
import { OtpInput, type OtpInputHandle } from "./otp-input";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { asApiError, type ApiError } from "@/lib/api-client";
import { sanitizeApiMessage } from "@/lib/form-errors";
import {
  getOtpExpirySeconds,
  getOtpResendCooldownSeconds,
} from "@/lib/config";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { pendingRegistration } from "../lib/pending-registration";
import { useExpiryCountdown, useCooldown } from "../hooks/use-otp-countdown";

const OTP_LOCKED_CODE = "AUTH_OTP_LOCKED";
const OTP_INVALID_CODE = "AUTH_OTP_INVALID";
const OTP_EXPIRED_CODE = "AUTH_OTP_EXPIRED";
const OTP_RESEND_COOLDOWN_CODE = "AUTH_RATE_LIMIT_EXCEEDED";
const OTP_DAILY_LIMIT_CODE = "AUTH_OTP_DAILY_LIMIT_EXCEEDED";
const COOLDOWN_MESSAGE_PATTERN = /(\d+)\s*(giây|seconds|s)\b/i;

function extractCooldownSeconds(message: string | undefined | null): number | null {
  if (!message) return null;
  const match = message.match(COOLDOWN_MESSAGE_PATTERN);
  if (!match) return null;
  const seconds = Number.parseInt(match[1], 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}

export function OtpForm() {
  const t = useTranslations("auth.otp");
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((state) => state.setAuth);
  const isAuthenticated = useAuthStore((state) => !!state.accessToken);

  const userId = searchParams.get("userId") || "";
  const [otpCode, setOtpCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [otpIssuedAt, setOtpIssuedAt] = useState<number | null>(() => Date.now());
  const [otpInvalid, setOtpInvalid] = useState(false);

  const otpInputRef = useRef<OtpInputHandle>(null);

  useEffect(() => {
    if (isAuthenticated) {
      router.replace("/");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    if (!userId) {
      const fallback = pendingRegistration.get();
      if (fallback) {
        router.replace(`/verify-otp?userId=${encodeURIComponent(fallback)}`);
      } else {
        router.replace("/register");
      }
    }
  }, [userId, router]);

  const otpExpiryTtl = getOtpExpirySeconds();
  const resendCooldownTtl = getOtpResendCooldownSeconds();

  const { remaining: otpExpiry } = useExpiryCountdown({
    ttlSeconds: otpExpiryTtl,
    serverTimestamp: otpIssuedAt,
  });

  const cooldown = useCooldown({ ttlSeconds: resendCooldownTtl });

  const { mutate: verifyMutate, isPending: isVerifying } = useVerifyOtp();
  const { mutate: resendMutate, isPending: isResending } = useResendOtp();

  const handleVerify = (e: React.FormEvent) => {
    e.preventDefault();
    if (!userId) return;
    if (otpCode.length !== 6) {
      setError(t("lengthError"));
      setOtpInvalid(true);
      return;
    }
    if (otpExpiry === 0) {
      setError(t("expiredError"));
      return;
    }

    setError(null);
    setOtpInvalid(false);

    verifyMutate(
      { data: { userId, code: otpCode } },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            const data = response.data;
            const expiresAt = decodeJwtExpiry(data.accessToken);
            setAuth(data.accessToken, {
              userId: data.userId,
              email: data.email,
              fullName: data.fullName ?? "",
              role: data.role,
              status: data.status,
              oauthProvider: "LOCAL",
            }, expiresAt ?? undefined);
            pendingRegistration.clear();
            toast.success(t("successToast"));
            router.push("/");
          } else {
            setError(response.message || t("errorToast"));
            setOtpInvalid(true);
          }
        },
        onError: asApiError<unknown>((err: ApiError) => {
          const errorCode = err.errors?.[0]?.code;
          if (errorCode === OTP_LOCKED_CODE) {
            setOtpIssuedAt(Date.now() - otpExpiryTtl * 1000);
            setError(t("lockedError"));
            setOtpInvalid(true);
            return;
          }
          if (errorCode === OTP_EXPIRED_CODE) {
            setOtpIssuedAt(Date.now() - otpExpiryTtl * 1000);
            setError(t("expiredError"));
            setOtpInvalid(true);
            return;
          }
          const apiError = sanitizeApiMessage(err, t("verificationFailed"));
          setError(apiError);
          setOtpInvalid(true);
          if (errorCode !== OTP_INVALID_CODE) {
            toast.error(t("errorToast"));
          }
        }),
      },
    );
  };

  const handleResend = () => {
    if (cooldown.remaining > 0) return;
    if (!userId) return;

    setError(null);
    setOtpInvalid(false);

    resendMutate(
      { data: { userId, purpose: "REGISTER" as const } },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            const cooldownSeconds = extractCooldownSeconds(response.data.message);

            if (cooldownSeconds !== null) {
              cooldown.setFromServer(Date.now(), cooldownSeconds);
              setOtpIssuedAt(Date.now());
              return;
            }

            toast.success(t("resendSuccess"));
            setOtpIssuedAt(Date.now());
            cooldown.reset();
            otpInputRef.current?.clear();
            otpInputRef.current?.flash();
          } else {
            setError(response.message || t("resendFailed"));
            toast.error(t("resendFailedToast"));
          }
        },
        onError: asApiError<unknown>((err: ApiError) => {
          const errorCode = err.errors?.[0]?.code;
          if (errorCode === OTP_RESEND_COOLDOWN_CODE) {
            const cooldownSeconds = extractCooldownSeconds(err.errors?.[0]?.message);
            if (cooldownSeconds !== null) {
              cooldown.setFromServer(Date.now(), cooldownSeconds);
            } else {
              cooldown.setFromServer(Date.now(), resendCooldownTtl);
            }
          }
          if (errorCode === OTP_DAILY_LIMIT_CODE) {
            const apiError = sanitizeApiMessage(err, t("dailyLimitError"));
            setError(apiError);
            toast.error(t("dailyLimitError"));
            return;
          }
          const apiError = sanitizeApiMessage(err, t("resendFailed"));
          setError(apiError);
          toast.error(t("resendFailedToast"));
        }),
      },
    );
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  if (!userId) {
    return null;
  }

  return (
    <form onSubmit={handleVerify} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("otpTitle")}
        </h1>
        <button
          type="button"
          onClick={() => router.push("/register")}
          className="text-xs font-semibold text-neutral-500 dark:text-neutral-400 hover:text-black dark:hover:text-white hover:underline self-center"
        >
          {t("changeEmail")}
        </button>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          <p>{error}</p>
        </div>
      )}

      <div className="flex flex-col gap-4">
        <OtpInput
          ref={otpInputRef}
          disabled={isVerifying || otpExpiry === 0}
          invalid={otpInvalid}
          flashOnUpdate
          onChange={setOtpCode}
        />

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

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isVerifying || otpCode.length !== 6 || otpExpiry === 0}
        className="w-full justify-center h-10 font-bold"
      >
        {isVerifying ? (
          <span className="flex items-center gap-2">
            <Spinner size="sm" className="text-white dark:text-black" />
            {t("verifying")}
          </span>
        ) : (
          t("verifyBtn")
        )}
      </Button>

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400">
        {t("notReceivedText")}{" "}
        {cooldown.remaining > 0 ? (
          <span className="text-neutral-400 font-semibold cursor-not-allowed inline-flex items-center gap-1.5">
            {cooldown.remaining > 0 && (
              <Spinner size="sm" className="text-neutral-400" />
            )}
            {t("resendCooldownText", { seconds: cooldown.remaining })}
          </span>
        ) : (
          <button
            type="button"
            disabled={isResending}
            onClick={handleResend}
            className="font-semibold text-black dark:text-white hover:underline focus:outline-none inline-flex items-center gap-1.5"
          >
            {isResending ? (
              <>
                <Spinner size="sm" />
                {t("resending")}
              </>
            ) : (
              t("resendLink")
            )}
          </button>
        )}
      </div>
    </form>
  );
}