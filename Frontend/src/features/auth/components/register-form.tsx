"use client";

import { useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useRegister } from "../api/register";
import { useLoginWithGoogle } from "../api/login";
import { useAuthStore } from "../stores/use-auth-store";
import { useGoogleIdentity } from "../hooks/use-google-identity";
import { useRetryCountdown } from "../hooks/use-retry-countdown";
import { useCaptureReturnTo, useRedirectAfterLogin } from "@/hooks/use-return-to";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError, type ApiError } from "@/lib/api-client";
import { sanitizeApiMessage } from "@/lib/form-errors";
import type { AuthUser } from "../types";
import { mapAuthResponseToUser } from "../lib/map-auth-response";
import {
  EMAIL_REGEX,
  calculatePasswordStrength,
} from "../hooks/password-validators";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { pendingRegistration } from "../lib/pending-registration";
import { registerSchema, type RegisterFormValues } from "../schemas/register-schema";
import { applyFieldErrors } from "@/lib/form-errors";
import { EMAIL_ALREADY_TAKEN_CODES, IamErrorCode } from "../lib/iam-error-codes";
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

const RATE_LIMIT_CODE = IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED;
const EMAIL_EXISTS_CODES = EMAIL_ALREADY_TAKEN_CODES;
const FIELD_MAPPING: Record<string, string> = {
  email: "email",
  password: "password",
  fullName: "fullName",
};

export function RegisterForm() {
  const t = useTranslations("auth.register");
  const router = useRouter();
  const { mutate: registerMutate, isPending } = useRegister();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const isAuthenticated = useAuthStore((state) => !!state.accessToken);
  const retryCountdown = useRetryCountdown();

  useEffect(() => {
    if (isAuthenticated) {
      router.replace("/");
    }
  }, [isAuthenticated, router]);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    setFocus,
    formState: { errors, isValid },
  } = useForm<RegisterFormValues>({
    resolver: standardSchemaResolver(registerSchema),
    mode: "onChange",
    defaultValues: {
      email: "",
      password: "",
      fullName: "",
      confirmPassword: "",
    },
  });

  const email = watch("email");
  const password = watch("password");

  const emailFormatValid = EMAIL_REGEX.test(email.trim());
  const passwordStrength = calculatePasswordStrength(password);

  const showEmailError = !!errors.email || (email.trim().length > 0 && !emailFormatValid);
  const showPasswordError = !!errors.password;

  const handleGoogleCredentialRef = useRef<((idToken: string) => void) | null>(null);
  const { setContainerRef: googleContainerRef } = useGoogleIdentity((idToken) =>
    handleGoogleCredentialRef.current?.(idToken),
  );

  useCaptureReturnTo();

  const redirectAfterLogin = useRedirectAfterLogin();

  const handleGoogleCredential = useCallback(
    (idToken: string) => {
      loginWithGoogleMutate(
        { data: { idToken } },
        {
          onSuccess: (response) => {
            if (response.success && response.data) {
              const data = response.data;
              const user: AuthUser = mapAuthResponseToUser(data);
              toast.success(t("successToast"));
              setAuth(data.accessToken, user, decodeJwtExpiry(data.accessToken) ?? undefined);
              redirectAfterLogin("/");
            } else {
              toast.error(response.message || t("errorToast"));
            }
          },
          onError: asApiError<unknown>((err: ApiError) => {
            toast.error(sanitizeApiMessage(err, t("errorToast")));
          }),
        },
      );
    },
    [loginWithGoogleMutate, setAuth, redirectAfterLogin, t],
  );

  useEffect(() => {
    handleGoogleCredentialRef.current = handleGoogleCredential;
    return () => {
      handleGoogleCredentialRef.current = null;
    };
  }, [handleGoogleCredential]);

  const onSubmit = handleSubmit((values) => {
    setError("root", { message: undefined });

    registerMutate(
      {
        data: {
          email: values.email.trim(),
          password: values.password,
          fullName: values.fullName.trim(),
        },
      },
      {
        onSuccess: (response) => {
          if (response.success && response.data?.userId) {
            pendingRegistration.set(response.data.userId);
            toast.success(t("successToast"));
            router.push(
              `/verify-otp?userId=${encodeURIComponent(response.data.userId)}`,
            );
          } else {
            const fallback = sanitizeApiMessage(
              { errors: null, message: response.message } as unknown as ApiError,
              t("errorToast"),
            );
            setError("root", { message: fallback });
            toast.error(fallback);
          }
        },
        onError: asApiError<unknown>((err: ApiError) => {
          const errorCode = err.code;
          if (err.status === 429 || errorCode === RATE_LIMIT_CODE) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            const msg = err.retryAfterSeconds
              ? t("rateLimitErrorWithSeconds", { seconds: err.retryAfterSeconds })
              : t("rateLimitError");
            setError("root", { message: msg });
            toast.error(msg);
            return;
          }
          if (errorCode && EMAIL_EXISTS_CODES.has(errorCode)) {
            setError("root", { message: t("emailExistsHint") });
            toast.error(t("emailExistsHint"), {
              action: {
                label: t("emailExistsLoginCta"),
                onClick: () => router.push("/login"),
              },
            });
            setFocus("email");
            return;
          }
          applyFieldErrors(
            (name, error) => setError(name as keyof RegisterFormValues, error),
            err,
            FIELD_MAPPING,
            t("errorToast"),
            (msg) => {
              const safeMsg = sanitizeApiMessage(
                { errors: [{ message: msg }], message: msg } as unknown as ApiError,
                t("errorToast"),
              );
              setError("root", { message: safeMsg });
              toast.error(safeMsg);
            },
          );
        }),
      },
    );
  });

  return (
    <form data-auth-wide onSubmit={onSubmit} className="flex flex-col gap-4 font-sans" noValidate>
      <div className="flex flex-col gap-1 text-center">
        <h1 className={`text-2xl font-bold tracking-tight ${NEU_TEXT}`}>
          {t("registerTitle")}
        </h1>
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("registerDesc")}</p>
      </div>

      {errors.root?.message && (
        <div
          role="alert"
          aria-live="assertive"
          className={`neu-pressed rounded-2xl border-none p-3 text-xs font-semibold ${NEU_DANGER_TEXT}`}
        >
          <p>{errors.root.message}</p>
        </div>
      )}

      <div className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <Label htmlFor="fullName" className={NEU_LABEL}>{t("fullNameLabel")}</Label>
          <Input
            id="fullName"
            type="text"
            disabled={isPending}
            aria-invalid={!!errors.fullName}
            {...register("fullName")}
            placeholder={t("fullNamePlaceholder")}
            className={`${NEU_INPUT} h-11`}
          />
          {errors.fullName?.message && (
            <span className={NEU_ERROR_TEXT}>
              {t(errors.fullName.message as never)}
            </span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="email" className={NEU_LABEL}>{t("emailLabel")}</Label>
          <Input
            id="email"
            type="email"
            inputMode="email"
            disabled={isPending}
            aria-invalid={showEmailError}
            {...register("email")}
            placeholder={t("emailPlaceholder")}
            className={`${NEU_INPUT} h-11`}
          />
          {errors.email?.message ? (
            <span className={NEU_ERROR_TEXT}>
              {t(errors.email.message as never)}
            </span>
          ) : showEmailError ? (
            <span className={NEU_ERROR_TEXT}>
              {t("invalidEmail")}
            </span>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="password" className={NEU_LABEL}>{t("passwordRequirementsLabel")}</Label>
          <PasswordInput
            id="password"
            disabled={isPending}
            aria-invalid={showPasswordError}
            {...register("password")}
            placeholder={t("passwordPlaceholder")}
            className="h-11"
          />
          {errors.password?.message && (
            <span className={NEU_ERROR_TEXT}>
              {t(errors.password.message as never)}
            </span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="confirmPassword" className={NEU_LABEL}>{t("confirmPasswordLabel")}</Label>
          <PasswordInput
            id="confirmPassword"
            disabled={isPending}
            aria-invalid={!!errors.confirmPassword}
            {...register("confirmPassword")}
            placeholder={t("confirmPasswordPlaceholder")}
            className="h-11"
          />
          {errors.confirmPassword?.message && (
            <span className={NEU_ERROR_TEXT}>
              {t(errors.confirmPassword.message as never)}
            </span>
          )}
        </div>
      </div>

      {password.length > 0 && (
        <div className="grid gap-x-6 gap-y-2 sm:grid-cols-2">
          <PasswordStrengthBar strength={passwordStrength} />
          <PasswordRules password={password} />
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2 sm:items-center sm:gap-x-6">
        <NeuButton
          type="submit"
          variant="primary"
          size="lg"
          disabled={isPending || retryCountdown.isActive || !isValid || (email.trim().length > 0 && !emailFormatValid)}
          className="w-full"
        >
          {isPending ? (
            <span className="flex items-center gap-2">
              <Spinner size="sm" className="text-white" />
              {t("submitting")}
            </span>
          ) : retryCountdown.isActive ? (
            t("retryCountdownText", { seconds: retryCountdown.remaining })
          ) : (
            t("submit")
          )}
        </NeuButton>

        <div className="relative flex items-center sm:hidden">
          <div className="neu-pressed-sm h-1 flex-grow rounded-full border-none" />
          <span className={`mx-4 flex-shrink text-xs font-bold ${NEU_TEXT_MUTED}`}>{t("or")}</span>
          <div className="neu-pressed-sm h-1 flex-grow rounded-full border-none" />
        </div>

        {process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
          <div
            ref={googleContainerRef}
            className="flex w-full justify-center [&_iframe]:!visible"
          />
        )}
      </div>

      <div className={`text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
        {t("hasAccountText")}{" "}
        <Link href="/login" className={`font-bold hover:underline ${NEU_ACCENT_TEXT}`}>
          {t("loginLink")}
        </Link>
      </div>
    </form>
  );
}