"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRegister } from "../api/register";
import { useCheckUsername } from "../api/check-username";
import { useDebounce } from "@/hooks/use-debounce";
import { usePasswordStrength } from "../hooks/use-password-strength";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";

const USERNAME_MIN_LENGTH = 3;
const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/;
const PASSWORD_MIN_LENGTH = 8;
const DEBOUNCE_MS = 300;

export function RegisterForm() {
  const t = useTranslations("auth.register");
  const router = useRouter();
  const { mutate: registerMutate, isPending } = useRegister();

  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [usernameBlurred, setUsernameBlurred] = useState(false);
  const [confirmPasswordBlurred, setConfirmPasswordBlurred] = useState(false);
  const [fullNameBlurred, setFullNameBlurred] = useState(false);
  const [emailBlurred, setEmailBlurred] = useState(false);
  const [passwordBlurred, setPasswordBlurred] = useState(false);

  const debouncedUsername = useDebounce(username, DEBOUNCE_MS);

  const usernameCheckEnabled = process.env.NEXT_PUBLIC_CHECK_USERNAME_ENABLED === "true";
  const usernameIsLongEnough = debouncedUsername.trim().length >= USERNAME_MIN_LENGTH;

  const { data: checkData, isPending: isCheckingUsername } = useCheckUsername({
    username: debouncedUsername,
    queryConfig: {
      enabled: usernameCheckEnabled && usernameBlurred && usernameIsLongEnough,
    },
  });

  const usernameUnavailable =
    usernameCheckEnabled &&
    usernameBlurred &&
    usernameIsLongEnough &&
    !isCheckingUsername &&
    checkData != null &&
    checkData.data != null &&
    !checkData.data.available;

  const passwordStrength = usePasswordStrength(password);

  const isFormFilled =
    fullName.trim().length > 0 &&
    username.trim().length > 0 &&
    email.trim().length > 0 &&
    password.length > 0 &&
    confirmPassword.length > 0;

  const isFormValid =
    isFormFilled &&
    username.trim().length >= USERNAME_MIN_LENGTH &&
    USERNAME_PATTERN.test(username) &&
    password.length >= PASSWORD_MIN_LENGTH &&
    password === confirmPassword &&
    !usernameUnavailable;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!isFormFilled) {
      setError(t("fillAll"));
      return;
    }

    if (username.length < USERNAME_MIN_LENGTH || !USERNAME_PATTERN.test(username)) {
      setError(t("invalidUsername"));
      return;
    }

    if (password.length < PASSWORD_MIN_LENGTH) {
      setError(t("minPassword"));
      return;
    }

    if (password !== confirmPassword) {
      setError(t("passwordMismatch"));
      return;
    }

    if (usernameUnavailable) {
      setError(t("usernameTaken"));
      return;
    }

    setError(null);

    registerMutate(
      {
        data: { fullName, username, email, password, confirmPassword },
      },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("successToast"));
            router.push(`/verify-otp?email=${encodeURIComponent(email)}`);
          } else {
            setError(response.message || t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          const apiError = err.errors?.[0]?.message;
          setError(apiError || err.message || t("errorToast"));
          toast.error(t("errorToast"));
        }),
      },
    );
  };

  const getUsernameHelperText = () => {
    if (!usernameCheckEnabled) return null;
    if (!usernameBlurred) return null;
    if (!usernameIsLongEnough) return null;
    if (isCheckingUsername) {
      return <span className="text-xs text-neutral-400 font-medium">{t("checkingUsername")}</span>;
    }
    if (checkData && checkData.data) {
      return checkData.data.available ? (
        <span className="text-xs text-neutral-600 dark:text-neutral-300 font-semibold">
          {t("usernameAvailable")}
        </span>
      ) : (
        <span className="text-xs text-red-600 dark:text-red-400 font-semibold">
          {t("usernameUnavailable")}
        </span>
      );
    }
    return null;
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5 font-sans" noValidate>
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("registerTitle")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("registerDesc")}</p>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/20 dark:text-red-400 border border-red-100/50 dark:border-red-950/30"
        >
          {error}
        </div>
      )}

      {/* Full Name */}
      <div className="flex flex-col gap-1">
        <Label htmlFor="fullName">{t("fullNameLabel")}</Label>
        <Input
          id="fullName"
          type="text"
          disabled={isPending}
          value={fullName}
          onChange={(e) => {
            setFullName(e.target.value);
            setFullNameBlurred(false);
          }}
          onBlur={() => setFullNameBlurred(true)}
          placeholder={t("fullNamePlaceholder")}
          required
        />
        {fullNameBlurred && fullName.trim().length === 0 && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("fullNameRequired")}
          </span>
        )}
      </div>

      {/* Username */}
      <div className="flex flex-col gap-1">
        <Label htmlFor="username">{t("usernameRequirementsLabel")}</Label>
        <Input
          id="username"
          type="text"
          disabled={isPending}
          value={username}
          onChange={(e) => {
            setUsername(e.target.value.replace(/[^a-zA-Z0-9_]/g, ""));
            setUsernameBlurred(false);
          }}
          onBlur={() => setUsernameBlurred(true)}
          placeholder={t("usernamePlaceholder")}
          required
        />
        {usernameBlurred && username.trim().length === 0 && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("usernameRequired")}
          </span>
        )}
        {usernameBlurred && username.trim().length > 0 && (username.trim().length < USERNAME_MIN_LENGTH || !USERNAME_PATTERN.test(username)) && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("invalidUsername")}
          </span>
        )}
        {getUsernameHelperText() && (
          <div className="min-h-4 mt-0.5">{getUsernameHelperText()}</div>
        )}
      </div>

      {/* Email */}
      <div className="flex flex-col gap-1">
        <Label htmlFor="email">{t("emailLabel")}</Label>
        <Input
          id="email"
          type="email"
          inputMode="email"
          disabled={isPending}
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            setEmailBlurred(false);
          }}
          onBlur={() => setEmailBlurred(true)}
          placeholder={t("emailPlaceholder")}
          required
        />
        {emailBlurred && email.trim().length === 0 && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("emailRequired")}
          </span>
        )}
        {emailBlurred && email.trim().length > 0 && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("invalidEmail")}
          </span>
        )}
      </div>

      {/* Password */}
      <div className="flex flex-col gap-1">
        <Label htmlFor="password">{t("passwordRequirementsLabel")}</Label>
        <PasswordInput
          id="password"
          disabled={isPending}
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            setPasswordBlurred(false);
          }}
          onBlur={() => setPasswordBlurred(true)}
          placeholder={t("passwordPlaceholder")}
          required
        />
        {passwordBlurred && password.length === 0 && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("passwordRequired")}
          </span>
        )}
        {passwordBlurred && password.length > 0 && password.length < PASSWORD_MIN_LENGTH && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t("minPassword")}
          </span>
        )}
        <PasswordStrengthBar strength={passwordStrength} />
      </div>

      {/* Confirm Password */}
      <div className="flex flex-col gap-1">
        <Label htmlFor="confirmPassword">{t("confirmPasswordLabel")}</Label>
        <PasswordInput
          id="confirmPassword"
          disabled={isPending}
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            setConfirmPasswordBlurred(false);
          }}
          onBlur={() => setConfirmPasswordBlurred(true)}
          placeholder={t("confirmPasswordPlaceholder")}
          required
        />
        {password.length > 0 &&
          confirmPassword.length > 0 &&
          password !== confirmPassword &&
          (confirmPasswordBlurred || confirmPassword.length >= password.length) && (
            <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
              {t("passwordMismatch")}
            </span>
          )}
      </div>

      {/* Submit Button */}
      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || !isFormValid}
        className="w-full justify-center h-10 font-bold mt-2"
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
        ) : (
          t("submit")
        )}
      </Button>

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400 mt-2">
        {t("hasAccountText")}{" "}
        <Link href="/login" className="font-semibold text-black dark:text-white hover:underline">
          {t("loginLink")}
        </Link>
      </div>
    </form>
  );
}
