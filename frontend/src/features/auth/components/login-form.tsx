"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import Script from "next/script";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useLogin, useLoginWithGoogle } from "../api/login";
import { useAuthStore } from "../stores/use-auth-store";
import { useGoogleIdentity } from "../hooks/use-google-identity";
import { useCaptureReturnTo, readReturnTo } from "@/hooks/use-return-to";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError } from "@/lib/api-client";
import type { AuthUser } from "../types";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";

export function LoginForm() {
  const t = useTranslations("auth.login");
  const router = useRouter();
  const { mutate: loginMutate, isPending } = useLogin();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const emailInputRef = useRef<HTMLInputElement>(null);
  const handleGoogleCredentialRef = useRef<((idToken: string) => void) | null>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { ready: googleReady, triggerClick: googleTriggerClick, handleLoad: googleHandleLoad, setContainerRef: googleContainerRef, scriptSrc: googleScriptSrc, scriptId: googleScriptId } = useGoogleIdentity(
    (idToken) => handleGoogleCredentialRef.current?.(idToken),
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
      const target = readReturnTo();
      if (target) {
        router.push(target);
      } else {
        router.push("/dashboard");
      }
    },
    [router],
  );

  const handleAuthSuccess = useCallback(
    (accessToken: string, user: AuthUser) => {
      const expiresAt = decodeJwtExpiry(accessToken);
      setAuth(accessToken, user, expiresAt ?? undefined);
      if (rememberMe && email.trim().length > 0) {
        localStorage.setItem("login_email", email.trim());
        localStorage.setItem("login_remember", "true");
      } else {
        localStorage.removeItem("login_email");
        localStorage.removeItem("login_remember");
      }
    },
    [rememberMe, email, setAuth],
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

  const handleGoogleLogin = () => {
    if (!googleReady) {
      toast.error(t("googleLoginUnavailable"));
      return;
    }
    googleTriggerClick();
  };

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

      <div className="flex items-center gap-2">
        <Checkbox
          id="rememberMe"
          checked={rememberMe}
          onCheckedChange={(checked: boolean) => setRememberMe(!!checked)}
          disabled={isPending}
          tabIndex={3}
        />
        <Label
          htmlFor="rememberMe"
          className="text-sm font-normal text-neutral-600 dark:text-neutral-400 cursor-pointer"
        >
          {t("rememberMe")}
        </Label>
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending}
        className="w-full justify-center h-10 font-bold"
        tabIndex={4}
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
        <Script
          id={googleScriptId}
          src={googleScriptSrc}
          strategy="afterInteractive"
          async
          defer
          onLoad={googleHandleLoad}
        />
      )}
      <div
        ref={googleContainerRef}
        style={{ position: "absolute", width: 0, height: 0, overflow: "hidden", pointerEvents: "none" }}
        aria-hidden="true"
      />

      <Button
        type="button"
        variant="outline"
        size="lg"
        disabled={isPending || !googleReady}
        onClick={handleGoogleLogin}
        className="w-full justify-center gap-2 border-neutral-200 dark:border-neutral-800 hover:bg-neutral-50 dark:hover:bg-neutral-900"
      >
        <svg className="size-4" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12.24 10.285V14.4h6.887c-.648 2.41-2.519 4.114-5.136 4.114-3.524 0-6.386-2.862-6.386-6.386 0-3.524 2.862-6.386 6.386-6.386 1.63 0 3.116.618 4.256 1.63l3.056-3.056C19.34 2.502 16.035 1 12.24 1 6.136 1 1.18 5.956 1.18 12.06c0 6.104 4.956 11.06 11.06 11.06 6.368 0 11.06-4.475 11.06-11.06 0-.745-.074-1.463-.207-2.149H12.24z" />
        </svg>
        {t("googleBtn")}
      </Button>

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400">
        {t("noAccount")}{" "}
        <Link href="/register" className="font-semibold text-black dark:text-white hover:underline">
          {t("registerLink")}
        </Link>
      </div>
    </form>
  );
}
