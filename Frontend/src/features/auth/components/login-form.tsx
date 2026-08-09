"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useLogin, useLoginWithGoogle } from "../api/login";
import { useAuthStore } from "../stores/use-auth-store";
import { useGoogleIdentity } from "../hooks/use-google-identity";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { useCaptureReturnTo, useRedirectAfterLogin } from "@/hooks/use-return-to";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError } from "@/lib/api-client";
import { IamErrorCode } from "../lib/iam-error-codes";
import { loginSchema, type LoginFormValues } from "../schemas/login-schema";
import { sanitizeApiMessage } from "@/lib/form-errors";
import type { AuthUser } from "../types";
import { mapAuthResponseToUser } from "../lib/map-auth-response";
import { PasswordInput } from "./password-input";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import {
  NEU_ACCENT_TEXT,
  NEU_DANGER_TEXT,
  NEU_ERROR_TEXT,
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";

export function LoginForm() {
  const t = useTranslations("auth.login");

  const router = useRouter();
  const locale = useLocale();
  const { mutate: loginMutate, isPending } = useLogin();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const isAuthenticated = useAuthStore((state) => !!state.accessToken);
  const retryCountdown = useRetryCountdown();

  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: standardSchemaResolver(loginSchema),
    mode: "onSubmit",
    defaultValues: { email: "", password: "" },
  });

  // `root` carries whatever the server rejected the whole attempt with.
  const formError = errors.root?.message ?? null;
  // Drives the dimmed/disabled state of the Google button, so it has to trigger a re-render.
  const [isGooglePending, setIsGooglePending] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      router.replace("/");
    }
  }, [isAuthenticated, router]);

  const handleGoogleCredentialRef = useRef<((idToken: string) => void) | null>(null);
  const { setContainerRef: googleContainerRef } = useGoogleIdentity(
    (idToken) => handleGoogleCredentialRef.current?.(idToken),
    locale,
  );

  useCaptureReturnTo();

  useEffect(() => {
    setFocus("email");
  }, [setFocus]);

  // Sends the user back where the 401 interceptor bounced them from, falling back to home.
  const redirectAfterLogin = useRedirectAfterLogin();

  const handleAuthSuccess = useCallback(
    (accessToken: string, user: AuthUser) => {
      const expiresAt = decodeJwtExpiry(accessToken);
      setAuth(accessToken, user, expiresAt ?? undefined);
    },
    [setAuth],
  );

  const onSubmit = handleSubmit((values) => {
    clearErrors("root");
    loginMutate(
      { data: { email: values.email.trim(), password: values.password } },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            const data = response.data;
            const user: AuthUser = mapAuthResponseToUser(data);
            toast.success(t("successToast"));
            handleAuthSuccess(data.accessToken, user);
            broadcastAuthMessage({ type: "TOKEN_UPDATED", token: data.accessToken, user });
            redirectAfterLogin("/");
          } else {
            setError("root", { message: response.message || t("errorToast") });
          }
        },
        onError: asApiError((err) => {
          if (err.code === IamErrorCode.ACCOUNT_LOCKED) {
            const lockedMsg = t("accountLocked");
            setError("root", { message: lockedMsg });
            toast.error(lockedMsg);
            return;
          }
          if (err.status === 429) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            setError("root", {
              message: err.retryAfterSeconds
                ? t("rateLimitErrorWithSeconds", { seconds: retryCountdown.remaining })
                : t("errorToast"),
            });
            toast.error(
              err.retryAfterSeconds
                ? t("rateLimitToastWithSeconds", { seconds: err.retryAfterSeconds })
                : t("errorToast"),
            );
            return;
          }
          const errorMsg = sanitizeApiMessage(err, t("errorToast"));
          setError("root", { message: errorMsg });
          toast.error(errorMsg);
        }),
      },
    );
  });

  const handleGoogleCredential = useCallback(
    (idToken: string) => {
      setIsGooglePending(true);
      loginWithGoogleMutate(
        { data: { idToken } },
        {
          onSuccess: (response) => {
            if (response.success && response.data) {
              const data = response.data;
              const user: AuthUser = mapAuthResponseToUser(data);
              toast.success(t("successToast"));
              handleAuthSuccess(data.accessToken, user);
              broadcastAuthMessage({ type: "TOKEN_UPDATED", token: data.accessToken, user });
              redirectAfterLogin("/");
            } else {
              toast.error(response.message || t("errorToast"));
            }
            setIsGooglePending(false);
          },
          onError: asApiError((err) => {
            toast.error(err.message || t("errorToast"));
            setIsGooglePending(false);
          }),
        },
      );
    },
    [loginWithGoogleMutate, handleAuthSuccess, redirectAfterLogin, t],
  );

  useEffect(() => {
    handleGoogleCredentialRef.current = handleGoogleCredential;
    return () => {
      handleGoogleCredentialRef.current = null;
    };
  }, [handleGoogleCredential]);

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5 font-sans">
      <div className="flex flex-col gap-1 text-center">
        <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
          {t("title")}
        </h1>
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("subtitle")}</p>
      </div>

      {formError && (
        <div
          role="alert"
          aria-live="assertive"
          className={`neu-pressed rounded-2xl border-none p-3.5 text-xs font-semibold ${NEU_DANGER_TEXT}`}
        >
          <p>{formError}</p>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email" className={NEU_LABEL}>{t("emailLabel")}</Label>
        <Input
          id="email"
          type="email"
          inputMode="email"
          disabled={isPending}
          {...register("email")}
          placeholder={t("emailPlaceholder")}
          tabIndex={1}
          aria-invalid={!!errors.email || !!formError}
          autoComplete="email"
          className={`${NEU_INPUT} h-12`}
        />
        {errors.email?.message && (
          <span className={`mt-1 ${NEU_ERROR_TEXT}`}>
            {t(errors.email.message as never)}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <Label htmlFor="password" className={NEU_LABEL}>{t("passwordLabel")}</Label>
          <Link
            href="/forgot-password"
            className={`text-xs font-semibold hover:underline ${NEU_ACCENT_TEXT}`}
            tabIndex={4}
          >
            {t("forgotPassword")}
          </Link>
        </div>
        <PasswordInput
          id="password"
          disabled={isPending}
          {...register("password")}
          placeholder={t("passwordPlaceholder")}
          tabIndex={2}
          aria-invalid={!!errors.password || !!formError}
          autoComplete="current-password"
        />
        {errors.password?.message && (
          <span className={`mt-1 ${NEU_ERROR_TEXT}`}>
            {t(errors.password.message as never)}
          </span>
        )}
      </div>

      <NeuButton
        type="submit"
        variant="primary"
        size="lg"
        disabled={isPending || isSubmitting || retryCountdown.isActive}
        className="w-full"
        tabIndex={3}
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
        ) : retryCountdown.isActive ? (
          t("retryCountdownText", { seconds: retryCountdown.remaining })
        ) : (
          t("submit")
        )}
      </NeuButton>

      <div className="relative flex items-center">
        <div className="neu-pressed-sm h-1 flex-grow rounded-full border-none" />
        <span className={`mx-4 flex-shrink text-xs font-bold ${NEU_TEXT_MUTED}`}>{t("or")}</span>
        <div className="neu-pressed-sm h-1 flex-grow rounded-full border-none" />
      </div>

      {process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <div
          ref={googleContainerRef}
          data-google-button-container
          className={`flex justify-center w-full [&_iframe]:!visible ${isGooglePending ? "pointer-events-none opacity-50" : ""}`}
        />
      )}

      <div className={`text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
        {t("noAccount")}{" "}
        <Link href="/register" className={`font-bold hover:underline ${NEU_ACCENT_TEXT}`}>
          {t("registerLink")}
        </Link>
      </div>
    </form>
  );
}
