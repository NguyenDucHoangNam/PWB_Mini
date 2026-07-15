"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useLogin, useLoginWithGoogle } from "../api/login";
import { useAuthStore } from "../stores/use-auth-store";
import { useGoogleIdentity } from "../hooks/use-google-identity";
import { useCaptureReturnTo } from "@/hooks/use-return-to";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError } from "@/lib/api-client";
import type { AuthUser } from "../types";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export function LoginForm() {
  const t = useTranslations("auth.login");
  const router = useRouter();
  const { mutate: loginMutate, isPending } = useLogin();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const emailInputRef = useRef<HTMLInputElement>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleGoogleCredentialRef = useRef<((idToken: string) => void) | null>(null);
  const { setContainerRef: googleContainerRef } = useGoogleIdentity((idToken) =>
    handleGoogleCredentialRef.current?.(idToken),
  );

  useCaptureReturnTo();

  useEffect(() => {
    if (emailInputRef.current) {
      emailInputRef.current.focus();
    }
  }, []);

  const redirectAfterLogin = useCallback(
    (nextStep?: string) => {
      if (nextStep === "COMPLETE_PROFILE") {
        router.push("/complete-profile");
        return;
      }
      router.push("/");
    },
    [router],
  );

  const handleAuthSuccess = useCallback(
    (accessToken: string, user: AuthUser) => {
      const expiresAt = decodeJwtExpiry(accessToken);
      setAuth(accessToken, user, expiresAt ?? undefined);
    },
    [setAuth],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError(t("fillAll"));
      return;
    }
    setError(null);
    loginMutate(
      { data: { email: email.trim(), password } },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            const data = response.data;
            const user: AuthUser = {
              userId: data.userId,
              email: data.email,
              username: "",
              role: data.role,
              status: data.status,
              oauthProvider: "LOCAL",
            };
            toast.success(t("successToast"));
            handleAuthSuccess(data.accessToken, user);
            redirectAfterLogin(data.nextStep);
          } else {
            setError(response.message || t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          setError(err.message || t("errorToast"));
          toast.error(t("errorToast"));
        }),
      },
    );
  };

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
                username: "",
                role: data.role,
                status: data.status,
                oauthProvider: "GOOGLE",
              };
              toast.success(t("successToast"));
              handleAuthSuccess(data.accessToken, user);
              redirectAfterLogin(data.nextStep);
            } else {
              toast.error(response.message || t("errorToast"));
            }
          },
          onError: asApiError((err) => {
            toast.error(err.message || t("errorToast"));
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
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

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email">{t("emailLabel")}</Label>
        <Input
          ref={emailInputRef}
          id="email"
          type="email"
          inputMode="email"
          disabled={isPending}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder={t("emailPlaceholder")}
          required
          tabIndex={1}
          aria-invalid={!!error}
          autoComplete="email"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <Label htmlFor="password">{t("passwordLabel")}</Label>
          <Link
            href="/forgot-password"
            className="text-xs font-medium text-neutral-500 hover:text-black dark:hover:text-white hover:underline"
            tabIndex={4}
          >
            {t("forgotPassword")}
          </Link>
        </div>
        <PasswordInput
          id="password"
          disabled={isPending}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder={t("passwordPlaceholder")}
          required
          tabIndex={2}
          aria-invalid={!!error}
          autoComplete="current-password"
        />
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending}
        className="w-full justify-center h-10 font-bold"
        tabIndex={3}
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

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400">
        {t("noAccount")}{" "}
        <Link href="/register" className="font-semibold text-black dark:text-white hover:underline">
          {t("registerLink")}
        </Link>
      </div>
    </form>
  );
}
