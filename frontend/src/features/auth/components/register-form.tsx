"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useRegister } from "../api/register";
import { CaptchaWidget } from "./captcha-widget";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import { getTurnstileSiteKey } from "@/lib/config";
import {
  EMAIL_REGEX,
  calculatePasswordStrength,
} from "../hooks/password-validators";
import { registerSchema, type RegisterFormValues } from "../schemas/register-schema";
import { applyFieldErrors } from "@/lib/form-errors";

const RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";
const FIELD_MAPPING: Record<string, string> = {
  email: "email",
  password: "password",
  fullName: "fullName",
};

export function RegisterForm() {
  const t = useTranslations("auth.register");
  const router = useRouter();
  const turnstileSiteKey = getTurnstileSiteKey();
  const { mutate: registerMutate, isPending } = useRegister();

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors, isValid },
  } = useForm<RegisterFormValues>({
    resolver: standardSchemaResolver(registerSchema),
    mode: "onChange",
    defaultValues: {
      fullName: "",
      email: "",
      password: "",
      confirmPassword: "",
      captchaToken: undefined,
    },
  });

  const email = watch("email");
  const password = watch("password");
  const captchaToken = watch("captchaToken");

  const emailFormatValid = EMAIL_REGEX.test(email.trim());
  const passwordStrength = calculatePasswordStrength(password);

  const captchaRequired = turnstileSiteKey !== null;
  const isCaptchaReady = !captchaRequired || (typeof captchaToken === "string" && captchaToken.length > 0);

  const showFullNameError = !!errors.fullName;
  const showEmailError = !!errors.email || (email.trim().length > 0 && !emailFormatValid);
  const showPasswordError = !!errors.password;

  const onSubmit = handleSubmit((values) => {
    setError("root", { message: undefined });

    registerMutate(
      {
        data: {
          email: values.email.trim(),
          password: values.password,
          fullName: values.fullName.trim(),
          captchaToken: values.captchaToken ?? undefined,
        },
      },
      {
        onSuccess: () => {
          toast.success(t("successToast"));
          router.push(`/verify-otp?email=${encodeURIComponent(values.email.trim())}`);
        },
        onError: asApiError((err) => {
          if (err.status === 429 || err.errors?.[0]?.code === RATE_LIMIT_CODE) {
            setError("root", { message: t("rateLimitError") });
            toast.error(t("rateLimitError"));
            return;
          }
          applyFieldErrors(
            (name, error) => setError(name as keyof RegisterFormValues, error),
            err,
            FIELD_MAPPING,
            t("errorToast"),
            (msg) => {
              setError("root", { message: msg });
              toast.error(t("errorToast"));
            },
          );
        }),
      },
    );
  });

  const handleCaptchaTokenChange = (token: string | null) => {
    setValue("captchaToken", token ?? undefined, { shouldValidate: true });
  };

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
          {errors.root.message}
        </div>
      )}

      <div className="flex flex-col gap-1">
        <Label htmlFor="fullName">{t("fullNameLabel")}</Label>
        <Input
          id="fullName"
          type="text"
          disabled={isPending}
          aria-invalid={showFullNameError}
          {...register("fullName")}
          placeholder={t("fullNamePlaceholder")}
        />
        {showFullNameError && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {errors.fullName?.message ? t(errors.fullName.message as never) : t("fullNameRequired")}
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

      {turnstileSiteKey && (
        <CaptchaWidget siteKey={turnstileSiteKey} onTokenChange={handleCaptchaTokenChange} />
      )}

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || !isValid || !isCaptchaReady || (email.trim().length > 0 && !emailFormatValid)}
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

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400 mt-2">
        {t("hasAccountText")}{" "}
        <Link href="/login" className="font-semibold text-black dark:text-white hover:underline">
          {t("loginLink")}
        </Link>
      </div>
    </form>
  );
}