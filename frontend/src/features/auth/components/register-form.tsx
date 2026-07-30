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
import { useCaptureReturnTo } from "@/hooks/use-return-to";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError, type ApiError } from "@/lib/api-client";
import { sanitizeApiMessage } from "@/lib/form-errors";
import type { AuthUser } from "../types";
import {
  EMAIL_REGEX,
  calculatePasswordStrength,
} from "../hooks/password-validators";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { PasswordRules } from "./password-rules";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { pendingRegistration } from "../lib/pending-registration";
import { registerSchema, type RegisterFormValues } from "../schemas/register-schema";
import { applyFieldErrors } from "@/lib/form-errors";

const RATE_LIMIT_CODE = "AUTH_RATE_LIMIT_EXCEEDED";
const EMAIL_EXISTS_CODES = new Set([
  "EMAIL_ALREADY_REGISTERED_AUTH",
  "EMAIL_ALREADY_EXISTS",
  "IAM_001",
  "IAM_020",
]);
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

  const redirectAfterLogin = useCallback(
    (_nextStep?: string) => {
      router.push("/");
    },
    [router],
  );

  const handleGoogleCredential = useCallback(
    (idToken: string) => {
      loginWithGoogleMutate(
        { data: { idToken } },
        {
          onSuccess: (response) => {
            if (response.success && response.data) {
              const data = response.data;
              const user: AuthUser = {
                userId: data.userId,
                email: data.email,
                fullName: data.fullName ?? "",
                role: data.role,
                status: data.status,
                oauthProvider: "GOOGLE",
              };
              toast.success(t("successToast"));
              setAuth(data.accessToken, user, decodeJwtExpiry(data.accessToken) ?? undefined);
              redirectAfterLogin(data.nextStep);
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
          const errorCode = err.errors?.[0]?.code;
          if (err.status === 429 || errorCode === RATE_LIMIT_CODE) {
            setError("root", { message: t("rateLimitError") });
            toast.error(t("rateLimitError"));
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
    <form onSubmit={onSubmit} className="flex flex-col gap-5 font-sans" noValidate>
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("registerTitle")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("registerDesc")}</p>
      </div>

      {errors.root?.message && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          <p>{errors.root.message}</p>
        </div>
      )}

      <div className="flex flex-col gap-1">
        <Label htmlFor="fullName">{t("fullNameLabel")}</Label>
        <Input
          id="fullName"
          type="text"
          disabled={isPending}
          aria-invalid={!!errors.fullName}
          {...register("fullName")}
          placeholder={t("fullNamePlaceholder")}
        />
        {errors.fullName?.message && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.fullName.message as never)}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="email">{t("emailLabel")}</Label>
        <Input
          id="email"
          type="email"
          inputMode="email"
          disabled={isPending}
          aria-invalid={showEmailError}
          {...register("email")}
          placeholder={t("emailPlaceholder")}
        />
        {errors.email?.message ? (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.email.message as never)}
          </span>
        ) : showEmailError ? (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("invalidEmail")}
          </span>
        ) : null}
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="password">{t("passwordRequirementsLabel")}</Label>
        <PasswordInput
          id="password"
          disabled={isPending}
          aria-invalid={showPasswordError}
          {...register("password")}
          placeholder={t("passwordPlaceholder")}
        />
        {errors.password?.message && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.password.message as never)}
          </span>
        )}
        <PasswordStrengthBar strength={passwordStrength} />
        <PasswordRules password={password} />
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="confirmPassword">{t("confirmPasswordLabel")}</Label>
        <PasswordInput
          id="confirmPassword"
          disabled={isPending}
          aria-invalid={!!errors.confirmPassword}
          {...register("confirmPassword")}
          placeholder={t("confirmPasswordPlaceholder")}
        />
        {errors.confirmPassword?.message && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.confirmPassword.message as never)}
          </span>
        )}
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || !isValid || (email.trim().length > 0 && !emailFormatValid)}
        className="w-full justify-center h-10 font-bold mt-2"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <Spinner size="sm" className="text-white dark:text-black" />
            {t("submitting")}
          </span>
        ) : (
          t("submit")
        )}
      </Button>

      <div className="relative flex py-2 items-center">
        <div className="flex-grow border-t border-neutral-200 dark:border-neutral-800" />
        <span className="flex-shrink mx-4 text-xs text-neutral-400 font-medium">{t("or")}</span>
        <div className="flex-grow border-t border-neutral-200 dark:border-neutral-800" />
      </div>

      {process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <div
          ref={googleContainerRef}
          className="flex justify-center w-full [&_iframe]:!visible"
        />
      )}

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400 mt-2">
        {t("hasAccountText")}{" "}
        <Link href="/login" className="font-semibold text-black dark:text-white hover:underline">
          {t("loginLink")}
        </Link>
      </div>
    </form>
  );
}