"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useVerifyOtp } from "../api/verify-otp";
import { useResendOtp } from "../api/resend-otp";
import { useAuthStore } from "../stores/use-auth-store";
import { OtpInput, type OtpInputHandle } from "./otp-input";
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
import { mapAuthResponseToUser } from "../lib/map-auth-response";
import { IamErrorCode } from "../lib/iam-error-codes";
import {
  NEU_ACCENT_TEXT,
  NEU_DANGER_TEXT,
  NEU_FOCUS,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";

const OTP_LOCKED_CODE = IamErrorCode.AUTH_OTP_INVALID;
const OTP_INVALID_CODE = "AUTH_OTP_INVALID";
const OTP_EXPIRED_CODE = IamErrorCode.AUTH_OTP_EXPIRED;
const OTP_RESEND_COOLDOWN_CODE = IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED;
const OTP_DAILY_LIMIT_CODE = IamErrorCode.AUTH_OTP_DAILY_LIMIT_EXCEEDED;
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
            setAuth(data.accessToken, mapAuthResponseToUser(data), expiresAt ?? undefined);
            pendingRegistration.clear();
            toast.success(t("successToast"));
            router.push("/");
          } else {
            setError(response.message || t("errorToast"));
            setOtpInvalid(true);
          }
        },
        onError: asApiError<unknown>((err: ApiError) => {
          const errorCode = err.code;
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
          const errorCode = err.code;
          if (errorCode === OTP_RESEND_COOLDOWN_CODE) {
            const cooldownSeconds = extractCooldownSeconds(err.message);
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
        <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
          {t("otpTitle")}
        </h1>
        <button
          type="button"
          onClick={() => router.push("/register")}
          className={`self-center text-xs font-semibold hover:underline ${NEU_ACCENT_TEXT}`}
        >
          {t("changeEmail")}
        </button>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="assertive"
          className={`neu-pressed rounded-2xl border-none p-3.5 text-xs font-semibold ${NEU_DANGER_TEXT}`}
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

        <div className={`text-center text-xs font-semibold ${NEU_TEXT_MUTED}`}>
          {otpExpiry > 0 ? (
            <span className="flex items-center justify-center gap-1.5">
              {t("timeRemaining")}{" "}
              <span className={`font-mono text-sm font-bold ${NEU_TEXT}`}>
                {formatTime(otpExpiry)}
              </span>
            </span>
          ) : (
            <span className={`font-bold ${NEU_TEXT}`}>{t("expiredText")}</span>
          )}
        </div>
      </div>

      <NeuButton
        type="submit"
        variant="primary"
        size="lg"
        disabled={isVerifying || otpCode.length !== 6 || otpExpiry === 0}
        className="w-full"
      >
        {isVerifying ? (
          <span className="flex items-center gap-2">
            <Spinner size="sm" className="text-white" />
            {t("verifying")}
          </span>
        ) : (
          t("verifyBtn")
        )}
      </NeuButton>

      <div className={`text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
        {t("notReceivedText")}{" "}
        {cooldown.remaining > 0 ? (
          <span className={`inline-flex cursor-not-allowed items-center gap-1.5 font-semibold ${NEU_TEXT_MUTED}`}>
            {cooldown.remaining > 0 && (
              <Spinner size="sm" />
            )}
            {t("resendCooldownText", { seconds: cooldown.remaining })}
          </span>
        ) : (
          <button
            type="button"
            disabled={isResending}
            onClick={handleResend}
            className={`inline-flex items-center gap-1.5 font-bold hover:underline ${NEU_ACCENT_TEXT} ${NEU_FOCUS}`}
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