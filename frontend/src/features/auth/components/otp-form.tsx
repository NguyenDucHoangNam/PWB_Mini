"use client";

import { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useVerifyOtp, useResendOtp } from "../api/verify-otp";
import { useAuthStore } from "../stores/use-auth-store";
import { OtpInput } from "./otp-input";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import {
  getOtpExpirySeconds,
  getOtpResendCooldownSeconds,
} from "@/lib/config";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { pendingRegistration } from "../lib/pending-registration";
import { useExpiryCountdown, useCooldown } from "../hooks/use-otp-countdown";

const OTP_LOCKED_CODE = "AUTH_OTP_LOCKED";
const OTP_RESEND_COOLDOWN_CODE = "AUTH_OTP_RATE_LIMIT";

function parseRetryAfter(headers: Record<string, string> | undefined): number | null {
  if (!headers) return null;
  const raw = headers["retry-after"] ?? headers["Retry-After"];
  if (!raw) return null;
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || parsed <= 0) return null;
  return Math.floor(parsed);
}

function parseServerTimestamp(value: string | null | undefined): number | null {
  if (!value) return null;
  const ms = Date.parse(value);
  return Number.isFinite(ms) ? ms : null;
}

export function OtpForm() {
  const t = useTranslations("auth.otp");
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((state) => state.setAuth);

  const userId = searchParams.get("userId") || "";
  const [otpCode, setOtpCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [otpIssuedAt, setOtpIssuedAt] = useState<number | null>(null);
  const [otpInvalid, setOtpInvalid] = useState(false);

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

  useEffect(() => {
    if (otpIssuedAt === null) {
      const now = Date.now();
      setOtpIssuedAt(now);
      cooldown.reset();
    }
  }, [otpIssuedAt, cooldown]);

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
              username: "",
              role: data.role,
              status: data.status,
              oauthProvider: "LOCAL",
            }, expiresAt ?? undefined);
            pendingRegistration.clear();
            toast.success(t("successToast"));
            if (data.nextStep === "COMPLETE_PROFILE") {
              router.push("/complete-profile");
            } else {
              router.push("/dashboard");
            }
          } else {
            setError(response.message || t("errorToast"));
            setOtpInvalid(true);
          }
        },
        onError: asApiError((err) => {
          const errorCode = err.errors?.[0]?.code;
          if (errorCode === OTP_LOCKED_CODE) {
            setOtpIssuedAt(Date.now() - otpExpiryTtl * 1000);
            setError(t("lockedError"));
            setOtpInvalid(true);
            return;
          }
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("verificationFailed"));
          setOtpInvalid(true);
          toast.error(t("errorToast"));
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
      { data: { userId } },
      {
        onSuccess: (response) => {
          toast.success(t("resendSuccess"));
          setOtpIssuedAt(Date.now());
          cooldown.reset();
        },
        onError: asApiError((err) => {
          const errorCode = err.errors?.[0]?.code;
          if (err.status === 429 || errorCode === OTP_RESEND_COOLDOWN_CODE) {
            const retryAfter = parseRetryAfter(err.headers ?? undefined);
            cooldown.setFromServer(Date.now(), retryAfter ?? resendCooldownTtl);
          }
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("resendFailed"));
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
          disabled={isVerifying || otpExpiry === 0}
          invalid={otpInvalid}
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
          <span className="text-neutral-400 font-semibold cursor-not-allowed">
            {t("resendCooldownText", { seconds: cooldown.remaining })}
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
