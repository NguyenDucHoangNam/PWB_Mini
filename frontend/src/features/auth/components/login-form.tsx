"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useLogin, useLoginWithGoogle } from "../api/login";
import { useAuthStore } from "../stores/use-auth-store";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";

const LOCKOUT_DURATION = 15 * 60; // 15 minutes in seconds
const STORAGE_KEY_USERNAME = "login_username";
const STORAGE_KEY_REMEMBER = "login_remember";

export function LoginForm() {
  const t = useTranslations("auth.login");
  const router = useRouter();
  const { mutate: loginMutate, isPending } = useLogin();
  const { mutate: loginWithGoogleMutate } = useLoginWithGoogle();
  const setAuth = useAuthStore((state) => state.setAuth);
  const usernameInputRef = useRef<HTMLInputElement>(null);

  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lockoutRemaining, setLockoutRemaining] = useState(0);

  // Load saved username and remember preference on mount
  useEffect(() => {
    const savedUsername = localStorage.getItem(STORAGE_KEY_USERNAME);
    const savedRemember = localStorage.getItem(STORAGE_KEY_REMEMBER);
    if (savedUsername && savedRemember === "true") {
      setUsernameOrEmail(savedUsername);
      setRememberMe(true);
    }
  }, []);

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

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Enter" && !isPending && lockoutRemaining === 0) {
        const target = e.target as HTMLElement;
        if (target.tagName !== "INPUT" && target.tagName !== "TEXTAREA") {
          return;
        }
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isPending, lockoutRemaining]);

  const formatLockoutTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  const handleRememberMeChange = (checked: boolean) => {
    setRememberMe(checked);
    if (checked) {
      localStorage.setItem(STORAGE_KEY_REMEMBER, "true");
    } else {
      localStorage.removeItem(STORAGE_KEY_REMEMBER);
    }
  };

  const handleUsernameChange = (value: string) => {
    setUsernameOrEmail(value);
    // Save username for form persistence
    if (value && rememberMe) {
      localStorage.setItem(STORAGE_KEY_USERNAME, value);
    } else {
      localStorage.removeItem(STORAGE_KEY_USERNAME);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!usernameOrEmail || !password) {
      setError(t("fillAll"));
      return;
    }

    setError(null);

    loginMutate(
      {
        data: { usernameOrEmail, password },
      },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            setAuth(response.data.accessToken, response.data.user);
            // Update remember me storage on successful login
            if (rememberMe) {
              localStorage.setItem(STORAGE_KEY_USERNAME, usernameOrEmail);
              localStorage.setItem(STORAGE_KEY_REMEMBER, "true");
            } else {
              localStorage.removeItem(STORAGE_KEY_USERNAME);
              localStorage.removeItem(STORAGE_KEY_REMEMBER);
            }
            toast.success(t("successToast"));
            router.push("/dashboard");
          } else {
            setError(response.message || t("errorToast"));
          }
        },
        onError: (err: any) => {
          const apiError = err.errors?.[0];
          const errorCode = apiError?.code;

          if (errorCode === "BAD_CREDENTIALS") {
            setError(t("incorrectCredentials"));
          } else if (errorCode === "ACCOUNT_TEMPORARILY_LOCKED") {
            setError(t("accountLocked"));
            setLockoutRemaining(LOCKOUT_DURATION);
          } else if (errorCode === "REGISTRATION_IN_PROGRESS") {
            // Redirect to OTP verification page with email
            const redirectTo = err.data?.redirectTo || "/verify-otp";
            const email = err.data?.email || usernameOrEmail;
            router.push(`${redirectTo}?email=${encodeURIComponent(email)}`);
            return;
          } else if (errorCode === "ACCOUNT_BANNED") {
            setError(t("accountBanned"));
          } else {
            setError(err.message || t("errorToast"));
          }
          toast.error(t("errorToast"));
        },
      }
    );
  };

  const handleGoogleLogin = () => {
    // Check if Google Identity Services is available
    if (typeof window !== "undefined" && (window as any).google) {
      const client = (window as any).google.accounts.oauth2.initTokenClient({
        client_id: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,
        scope: "email profile openid",
        callback: (response: any) => {
          if (response.error) {
            toast.error(t("googleLoginError") || "Google login failed");
            return;
          }
          loginWithGoogleMutate(
            { data: { idToken: response.access_token } },
            {
              onSuccess: (res) => {
                if (res.success && res.data) {
                  setAuth(res.data.accessToken, res.data.user);
                  toast.success(t("googleLoginSuccess") || "Login successful");
                  router.push("/dashboard");
                } else {
                  toast.error(res.message || t("errorToast"));
                }
              },
              onError: (err: any) => {
                const apiError = err.errors?.[0];
                if (apiError?.code === "ACCOUNT_BANNED") {
                  toast.error(t("accountBanned"));
                } else {
                  toast.error(err.message || t("googleLoginError") || "Google login failed");
                }
              },
            }
          );
        },
      });
      client.requestAccessToken();
    } else {
      toast.info(t("googleLoginUnavailable") || "Google login is not available");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
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

      {/* Username or Email Input */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="usernameOrEmail">{t("usernameLabel")}</Label>
        <Input
          ref={usernameInputRef}
          id="usernameOrEmail"
          type="text"
          disabled={isPending}
          value={usernameOrEmail}
          onChange={(e) => handleUsernameChange(e.target.value)}
          placeholder={t("usernamePlaceholder")}
          required
          tabIndex={1}
          aria-invalid={!!error}
          autoComplete="username"
        />
      </div>

      {/* Password Input */}
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

      {/* Remember Me */}
      <div className="flex items-center gap-2">
        <Checkbox
          id="rememberMe"
          checked={rememberMe}
          onCheckedChange={handleRememberMeChange}
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

      {/* Submit Button */}
      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || lockoutRemaining > 0}
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

      {/* Google Login button */}
      <Button
        type="button"
        variant="outline"
        size="lg"
        disabled={isPending || lockoutRemaining > 0}
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
        <Link
          href="/register"
          className="font-semibold text-black dark:text-white hover:underline"
        >
          {t("registerLink")}
        </Link>
      </div>
    </form>
  );
}
