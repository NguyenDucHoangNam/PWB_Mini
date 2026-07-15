"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useRegister } from "../api/register";
import { PasswordInput } from "./password-input";
import { PasswordStrengthBar } from "./password-strength-bar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import {
  EMAIL_REGEX,
  calculatePasswordStrength,
} from "../hooks/password-validators";
import { pendingRegistration } from "../lib/pending-registration";
import { registerSchema, type RegisterFormValues } from "../schemas/register-schema";
import { applyFieldErrors } from "@/lib/form-errors";

const RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";
const FIELD_MAPPING: Record<string, string> = {
  email: "email",
  password: "password",
};

export function RegisterForm() {
  const t = useTranslations("auth.register");
  const router = useRouter();
  const { mutate: registerMutate, isPending } = useRegister();

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isValid },
  } = useForm<RegisterFormValues>({
    resolver: standardSchemaResolver(registerSchema),
    mode: "onChange",
    defaultValues: {
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const email = watch("email");
  const password = watch("password");

  const emailFormatValid = EMAIL_REGEX.test(email.trim());
  const passwordStrength = calculatePasswordStrength(password);

  const showEmailError = !!errors.email || (email.trim().length > 0 && !emailFormatValid);
  const showPasswordError = !!errors.password;

  const onSubmit = handleSubmit((values) => {
    setError("root", { message: undefined });

    registerMutate(
      {
        data: {
          email: values.email.trim(),
          password: values.password,
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
            setError("root", { message: response.message || t("errorToast") });
            toast.error(t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          if (err.status === 429 || err.errors?.[0]?.code === RATE_LIMIT_CODE) {
            setError("root", { message: t("rateLimitError") });
            toast.error(t("rateLimitError"));
            return;
          }
          const errorCode = err.errors?.[0]?.code;
          if (errorCode === "USER_EMAIL_EXISTS") {
            setError("root", { message: t("emailExistsHint") });
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

      <div className="text-center text-sm text-neutral-500 dark:text-neutral-400 mt-2">
        {t("hasAccountText")}{" "}
        <Link href="/login" className="font-semibold text-black dark:text-white hover:underline">
          {t("loginLink")}
        </Link>
      </div>
    </form>
  );
}
