"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import Script from "next/script";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useLogin, useLoginWithGoogle } from "../api/login";
import { useAuthStore, type AuthUser } from "../stores/use-auth-store";
import { useGoogleIdentity } from "../hooks/use-google-identity";
import { useCaptureReturnTo, readReturnTo } from "@/hooks/use-return-to";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { asApiError, type ApiError } from "@/lib/api-client";
import { CaptchaWidget } from "./captcha-widget";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { getTurnstileSiteKey } from "@/lib/config";

const LOCKOUT_DURATION_FALLBACK = 15 * 60;
const STORAGE_KEY_USERNAME = "login_username";
const STORAGE_KEY_REMEMBER = "login_remember";

type CaptchaErrorCode = "CAPTCHA_MISSING" | "CAPTCHA_INVALID" | "CAPTCHA_SERVICE_UNAVAILABLE";

function parseRetryAfter(headers: Record<string, string> | undefined): number {
  if (!headers) return 0;
  const raw = headers["retry-after"] ?? headers["Retry-After"];
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

function readInitialRememberMe(): { username: string; remember: boolean } {
  if (typeof window === "undefined") return { username: "", remember: false };
  const savedUsername = localStorage.getItem(STORAGE_KEY_USERNAME);
  const savedRemember = localStorage.getItem(STORAGE_KEY_REMEMBER);
  if (savedUsername && savedRemember === "true") {
    return { username: savedUsername, remember: true };
  }
  return { username: "", remember: false };
}

export function LoginForm() {
  const t = useTranslations("auth.login");
  const router = useRouter();
  const { mutate: loginMutate, isPending } = useLogin();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const usernameInputRef = useRef<HTMLInputElement>(null);
  const turnstileSiteKey = getTurnstileSiteKey();
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const handleGoogleCredentialRef = useRef<((idToken: string) => void) | null>(null);

  const [usernameOrEmail, setUsernameOrEmail] = useState(() => readInitialRememberMe().username);
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(() => readInitialRememberMe().remember);
  const [error, setError] = useState<string | null>(null);
  const [lockoutRemaining, setLockoutRemaining] = useState(0);
  const [captchaRequired, setCaptchaRequired] = useState(false);
  const [captchaError, setCaptchaError] = useState<string | null>(null);
  const [googleCaptchaRequired, setGoogleCaptchaRequired] = useState(false);
  const [googleCaptchaToken, setGoogleCaptchaToken] = useState<string | null>(null);
  const { ready: googleReady, triggerClick: googleTriggerClick, handleLoad: googleHandleLoad, setContainerRef: googleContainerRef, scriptSrc: googleScriptSrc, scriptId: googleScriptId } = useGoogleIdentity(
    (idToken) => handleGoogleCredentialRef.current?.(idToken),
  );

  // Capture ?returnTo= so we can send the user back after login.
  useCaptureReturnTo();

  // Auto-focus username field on mount
  useEffect(() => {
    if (usernameInputRef.current) {
      usernameInputRef.current.focus();
    }
  }, []);

  // Handle countdown for lockout
  useEffect(() => {
    if (lockoutRemaining <= 0) return;
    const interval = setInterval(() => {
      setLockoutRemaining((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [lockoutRemaining]);

  const redirectAfterLogin = useCallback(() => {
    const target = readReturnTo();
    if (target) {
      router.push(target);
    } else {
      router.push("/dashboard");
    }
  }, [router]);

  const handleAuthSuccess = useCallback(
    (token: string, user: AuthUser) => {
      const expiresAt = decodeJwtExpiry(token);
      setAuth(token, user, expiresAt ?? undefined);

      if (rememberMe && usernameOrEmail.trim().length > 0) {
        localStorage.setItem(STORAGE_KEY_USERNAME, usernameOrEmail);
        localStorage.setItem(STORAGE_KEY_REMEMBER, "true");
      } else {
        localStorage.removeItem(STORAGE_KEY_USERNAME);
        localStorage.removeItem(STORAGE_KEY_REMEMBER);
      }

      // Route based on account status.
      if (user.status === "PENDING_DELETION") {
        router.push("/account-recovery");
        return;
      }
      redirectAfterLogin();
    },
    [rememberMe, usernameOrEmail, setAuth, router, redirectAfterLogin],
  );

  const handleLoginResponse = useCallback(
    (response: {
      success: boolean;
      data?: { accessToken: string; user: AuthUser } | null;
      message?: string;
    }) => {
      if (!response.success || !response.data) {
        return false;
      }
      toast.success(t("successToast"));
      handleAuthSuccess(response.data.accessToken, response.data.user);
      return true;
    },
    [t, handleAuthSuccess],
  );

  const resetCaptchaState = useCallback(() => {
    setCaptchaRequired(false);
    setCaptchaError(null);
    setCaptchaToken(null);
  }, []);

  const handleCaptchaChallenge = useCallback(
    (code: CaptchaErrorCode) => {
      setCaptchaRequired(true);
      setError(null);
      if (code === "CAPTCHA_MISSING") {
        setCaptchaError(t("captchaRequired"));
      } else if (code === "CAPTCHA_INVALID") {
        setCaptchaError(t("captchaInvalid"));
      } else {
        setCaptchaError(t("captchaServiceUnavailable"));
      }
    },
    [t],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!usernameOrEmail || !password) {
      setError(t("fillAll"));
      return;
    }
    if (captchaRequired && !captchaToken) {
      setError(t("captchaRequired"));
      return;
    }

    setError(null);

    loginMutate(
      {
        data: { usernameOrEmail, password, captchaToken: captchaToken ?? undefined },
      },
      {
        onSuccess: (response) => {
          if (!handleLoginResponse(response)) {
            resetCaptchaState();
            setError(response.message || t("errorToast"));
          } else {
            resetCaptchaState();
          }
        },
        onError: asApiError<ApiError>((err) => {
          const apiError = err?.errors?.[0];
          const errorCode = apiError?.code;

          if (errorCode === "CAPTCHA_MISSING" || errorCode === "CAPTCHA_INVALID" || errorCode === "CAPTCHA_SERVICE_UNAVAILABLE") {
            const messageKey =
              errorCode === "CAPTCHA_MISSING"
                ? "captchaRequired"
                : errorCode === "CAPTCHA_INVALID"
                  ? "captchaInvalid"
                  : "captchaServiceUnavailable";
            handleCaptchaChallenge(errorCode);
            toast.error(t(messageKey));
          } else if (errorCode === "BAD_CREDENTIALS") {
            setError(t("incorrectCredentials"));
            toast.error(t("incorrectCredentials"));
          } else if (errorCode === "ACCOUNT_TEMPORARILY_LOCKED") {
            setError(t("accountLocked"));
            toast.error(t("accountLocked"));
            const retrySeconds = parseRetryAfter(err?.headers);
            setLockoutRemaining(retrySeconds > 0 ? retrySeconds : LOCKOUT_DURATION_FALLBACK);
            resetCaptchaState();
          } else if (errorCode === "RATE_LIMIT_EXCEEDED") {
            const retrySeconds = parseRetryAfter(err?.headers);
            setLockoutRemaining(retrySeconds > 0 ? retrySeconds : LOCKOUT_DURATION_FALLBACK);
            setError(t("accountLocked"));
            toast.error(t("accountLocked"));
            resetCaptchaState();
          } else if (errorCode === "ACCOUNT_BANNED") {
            setError(t("accountBanned"));
            toast.error(t("accountBanned"));
          } else {
            setError(err?.message || t("errorToast"));
            toast.error(t("errorToast"));
          }
        }),
      },
    );
  };

  const handleGoogleCredential = useCallback(
    (idToken: string) => {
      loginWithGoogleMutate(
        { data: { idToken, captchaToken: googleCaptchaToken ?? undefined } },
        {
          onSuccess: (response) => {
            if (handleLoginResponse(response)) {
              setGoogleCaptchaRequired(false);
              setGoogleCaptchaToken(null);
              return;
            }
            toast.error(response.message || t("errorToast"));
          },
          onError: asApiError<ApiError>((err) => {
            const apiError = err?.errors?.[0];
            const errorCode = apiError?.code;

            if (
              errorCode === "CAPTCHA_MISSING" ||
              errorCode === "CAPTCHA_INVALID" ||
              errorCode === "CAPTCHA_SERVICE_UNAVAILABLE"
            ) {
              const messageKey =
                errorCode === "CAPTCHA_MISSING"
                  ? "googleCaptchaRequired"
                  : errorCode === "CAPTCHA_INVALID"
                    ? "captchaInvalid"
                    : "captchaServiceUnavailable";
              setGoogleCaptchaRequired(true);
              toast.error(t(messageKey));
            } else if (errorCode === "OAUTH_EMAIL_CONFLICT") {
              toast.error(t("oauthEmailConflict"));
            } else if (errorCode === "ACCOUNT_BANNED") {
              toast.error(t("accountBanned"));
            } else {
              toast.error(err?.message || t("googleLoginError"));
            }
          }),
        },
      );
    },
    [loginWithGoogleMutate, handleLoginResponse, googleCaptchaToken, t],
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
          {error === t("accountLocked") && (
            <Link href="/forgot-password" className="mt-1 block underline">
              {t("resetPasswordLink")}
            </Link>
          )}
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="usernameOrEmail">{t("usernameLabel")}</Label>
        <Input
          ref={usernameInputRef}
          id="usernameOrEmail"
          type="text"
          disabled={isPending}
          value={usernameOrEmail}
          onChange={(e) => setUsernameOrEmail(e.target.value)}
          placeholder={t("usernamePlaceholder")}
          required
          tabIndex={1}
          aria-invalid={!!error}
          autoComplete="username"
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
          error={error ? "true" : undefined}
          aria-invalid={!!error}
          autoComplete="current-password"
        />
      </div>

      <div className="flex items-center gap-2">
        <Checkbox
          id="rememberMe"
          checked={rememberMe}
          onCheckedChange={(checked: boolean) => {
            const next = !!checked;
            setRememberMe(next);
            if (next) {
              localStorage.setItem(STORAGE_KEY_REMEMBER, "true");
            } else {
              localStorage.removeItem(STORAGE_KEY_REMEMBER);
              localStorage.removeItem(STORAGE_KEY_USERNAME);
            }
          }}
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

      {captchaError && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-amber-50 p-3 text-xs font-semibold text-amber-700 dark:bg-amber-950/20 dark:text-amber-300 border border-amber-100/50 dark:border-amber-950/30"
        >
          <p>{captchaError}</p>
        </div>
      )}

      {turnstileSiteKey && captchaRequired && (
        <CaptchaWidget siteKey={turnstileSiteKey} onTokenChange={setCaptchaToken} />
      )}

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={
          isPending || lockoutRemaining > 0 || (captchaRequired && !captchaToken)
        }
        className="w-full justify-center h-10 font-bold"
        tabIndex={4}
      >
        {isPending ? (
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
            {t("submitting")}
          </span>
        ) : lockoutRemaining > 0 ? (
          formatLockoutTime(lockoutRemaining)
        ) : (
          t("submit")
        )}
      </Button>

      <div className="relative flex py-2 items-center">
        <div className="flex-grow border-t border-neutral-200 dark:border-neutral-800" />
        <span className="flex-shrink mx-4 text-xs text-neutral-400 font-medium">{t("or")}</span>
        <div className="flex-grow border-t border-neutral-200 dark:border-neutral-800" />
      </div>

      {/* Google Login - hidden GIS rendered button + visible custom button */}
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

      {turnstileSiteKey && googleCaptchaRequired && (
        <CaptchaWidget siteKey={turnstileSiteKey} onTokenChange={setGoogleCaptchaToken} />
      )}

      <Button
        type="button"
        variant="outline"
        size="lg"
        disabled={isPending || lockoutRemaining > 0 || !googleReady}
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

function formatLockoutTime(seconds: number) {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}
