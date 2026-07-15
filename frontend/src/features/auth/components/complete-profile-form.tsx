"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { useCompleteProfile } from "../api/complete-profile";
import { useAuthStore } from "../stores/use-auth-store";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import { decodeJwtExpiry } from "@/lib/jwt-decode";
import { completeProfileSchema, type CompleteProfileFormValues } from "../schemas/complete-profile-schema";
import { applyFieldErrors } from "@/lib/form-errors";

const FIELD_MAPPING: Record<string, string> = {
  username: "username",
  fullName: "fullName",
  newPassword: "newPassword",
};

export function CompleteProfileForm() {
  const t = useTranslations("auth.completeProfile");
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);
  const setUser = useAuthStore((state) => state.setUser);
  const existingUser = useAuthStore((state) => state.user);

  const { mutate: completeMutate, isPending } = useCompleteProfile();

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<CompleteProfileFormValues>({
    resolver: standardSchemaResolver(completeProfileSchema),
    mode: "onChange",
    defaultValues: {
      username: "",
      fullName: "",
      newPassword: "",
    },
  });

  const [error, setError] = useState<string | null>(null);

  const onSubmit = handleSubmit((values) => {
    setError(null);

    completeMutate(
      {
        data: {
          username: values.username.trim(),
          fullName: values.fullName?.trim() || undefined,
          newPassword: values.newPassword || undefined,
        },
      },
      {
        onSuccess: (response) => {
          if (response.success && response.data) {
            const data = response.data;
            const expiresAt = decodeJwtExpiry(data.accessToken);
            setAuth(data.accessToken, {
              userId: data.userId,
              email: data.email,
              username: values.username.trim(),
              role: data.role,
              status: data.status,
              oauthProvider: existingUser?.oauthProvider ?? "LOCAL",
            }, expiresAt ?? undefined);
            setUser({
              userId: data.userId,
              email: data.email,
              username: values.username.trim(),
              role: data.role,
              status: data.status,
              oauthProvider: existingUser?.oauthProvider ?? "LOCAL",
            });
            toast.success(t("successToast"));
            router.push("/");
          } else {
            setError(response.message || t("errorToast"));
            toast.error(t("errorToast"));
          }
        },
        onError: asApiError((err) => {
          applyFieldErrors(
            (name, fieldError) => setError(fieldError.message ?? ""),
            err,
            FIELD_MAPPING,
            t("errorToast"),
            (msg) => {
              setError(msg);
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
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("desc")}</p>
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

      <div className="flex flex-col gap-1">
        <Label htmlFor="username">{t("usernameLabel")}</Label>
        <Input
          id="username"
          type="text"
          disabled={isPending}
          aria-invalid={!!errors.username}
          {...register("username")}
          placeholder={t("usernamePlaceholder")}
        />
        {errors.username?.message && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.username.message as never)}
          </span>
        )}
      </div>

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
        <Label htmlFor="newPassword">{t("passwordLabel")}</Label>
        <PasswordInput
          id="newPassword"
          disabled={isPending}
          aria-invalid={!!errors.newPassword}
          {...register("newPassword")}
          placeholder={t("passwordPlaceholder")}
        />
        {errors.newPassword?.message && (
          <span className="text-xs text-red-600 dark:text-red-400 font-semibold mt-1">
            {t(errors.newPassword.message as never)}
          </span>
        )}
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending || !isValid}
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
    </form>
  );
}
