"use client";

import { useState, useEffect, useRef, useCallback, memo } from "react";
import Image from "next/image";
import { useTranslations } from "next-intl";
import { useProfile, useUpdateProfile } from "../api/profile";
import { useUploadAvatar } from "../api/account";
import { useAuthStore } from "../stores/use-auth-store";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

const MAX_AVATAR_BYTES = 2 * 1024 * 1024;
const ALLOWED_AVATAR_TYPES = ["image/jpeg", "image/png"] as const;

function validateFile(file: File, t: (key: string) => string): boolean {
  if (file.size > MAX_AVATAR_BYTES) {
    toast.error(t("avatarSizeError"));
    return false;
  }
  if (!ALLOWED_AVATAR_TYPES.includes(file.type as (typeof ALLOWED_AVATAR_TYPES)[number])) {
    toast.error(t("avatarTypeError"));
    return false;
  }
  return true;
}

type ProfileData = NonNullable<NonNullable<ReturnType<typeof useProfile>["data"]>["data"]>;

function AvatarDropzone({
  preview,
  fullName,
  disabled,
  onFileSelected,
}: {
  preview: string | null;
  fullName: string;
  disabled: boolean;
  onFileSelected: (file: File) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);

  const acceptFile = (file: File | null | undefined) => {
    if (file) onFileSelected(file);
  };

  return (
    <div
      onDragOver={(e) => {
        e.preventDefault();
        if (!disabled) setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={(e) => {
        e.preventDefault();
        setIsDragging(false);
        if (disabled) return;
        acceptFile(e.dataTransfer.files?.[0]);
      }}
      onClick={() => !disabled && inputRef.current?.click()}
      className={`group relative size-20 cursor-pointer rounded-full border-2 border-dashed bg-neutral-50 dark:bg-neutral-900 flex items-center justify-center overflow-hidden transition-colors ${
        isDragging
          ? "border-black dark:border-white"
          : "border-neutral-300 dark:border-neutral-700 hover:border-black dark:hover:border-white"
      }`}
      title="Drag & drop or click to change avatar"
    >
      {preview ? (
        <Image
          src={preview}
          alt="Avatar"
          width={80}
          height={80}
          unoptimized
          className="size-full object-cover group-hover:opacity-75 transition-opacity"
        />
      ) : (
        <span className="text-2xl font-bold text-neutral-400">
          {fullName ? fullName.charAt(0).toUpperCase() : "?"}
        </span>
      )}
      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
        <svg
          className="size-5 text-white"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
          />
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"
          />
        </svg>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept=".jpg,.jpeg,.png,image/jpeg,image/png"
        className="hidden"
        onChange={(e) => {
          acceptFile(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
    </div>
  );
}

function ProfileFormSkeleton() {
  return (
    <div className="flex flex-col gap-6 animate-pulse font-sans">
      <div className="flex items-center gap-6">
        <div className="size-20 rounded-full bg-neutral-200 dark:bg-neutral-800" />
        <div className="flex flex-col gap-2">
          <div className="h-4 w-24 bg-neutral-200 dark:bg-neutral-800 rounded" />
          <div className="h-3 w-40 bg-neutral-200 dark:bg-neutral-800 rounded" />
        </div>
      </div>
      <div className="space-y-4">
        {Array(4)
          .fill(null)
          .map((_, i) => (
            <div key={i} className="flex flex-col gap-2">
              <div className="h-4 w-20 bg-neutral-200 dark:bg-neutral-800 rounded" />
              <div className="h-10 w-full bg-neutral-200 dark:bg-neutral-800 rounded" />
            </div>
          ))}
      </div>
    </div>
  );
}

function ProfileFormError({
  t,
  onRetry,
}: {
  t: (key: string) => string;
  onRetry: () => void;
}) {
  return (
    <div className="flex flex-col gap-4 items-start font-sans">
      <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">
        {t("formTitle")}
      </h2>
      <div className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200">
        {t("loadError")}
      </div>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        {t("retry")}
      </Button>
    </div>
  );
}

function ProfileFormFieldsImpl({
  profile,
  authUsername,
}: {
  profile: ProfileData;
  authUsername: string | null;
}) {
  const t = useTranslations("profile");
  const { mutate: updateProfileMutate, isPending: isUpdating } = useUpdateProfile();
  const { mutate: uploadAvatarMutate, isPending: isUploadingAvatar } = useUploadAvatar();

  const [fullName, setFullName] = useState(profile.fullName || "");
  const [phone, setPhone] = useState(profile.phone || "");
  const [avatarPreview, setAvatarPreview] = useState<string | null>(profile.avatarUrl ?? null);
  const [avatarObjectUrl, setAvatarObjectUrl] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const pendingFileRef = useRef<File | null>(null);

  useEffect(() => {
    return () => {
      if (avatarObjectUrl) URL.revokeObjectURL(avatarObjectUrl);
    };
  }, [avatarObjectUrl]);

  const handleAvatarPicked = useCallback(
    (file: File) => {
      if (!validateFile(file, t)) return;
      if (avatarObjectUrl) URL.revokeObjectURL(avatarObjectUrl);
      const objectUrl = URL.createObjectURL(file);
      pendingFileRef.current = file;
      setAvatarObjectUrl(objectUrl);
      setAvatarPreview(objectUrl);
      toast.success(t("avatarSelected"));
    },
    [avatarObjectUrl, t],
  );

  const persistProfile = useCallback(
    (avatarUrl: string | null) => {
      updateProfileMutate(
        {
          data: {
            fullName,
            phone: phone || null,
            avatarUrl,
          },
        },
        {
          onSuccess: (response) => {
            if (response.success) {
              toast.success(t("saveSuccess"));
            } else {
              toast.error(response.message || t("saveError"));
            }
          },
          onError: (err: { message?: string }) => {
            setValidationError(err?.message || t("saveError"));
            toast.error(t("saveError"));
          },
        },
      );
    },
    [fullName, phone, t, updateProfileMutate],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!fullName.trim()) {
      setValidationError(t("fullNameRequired"));
      return;
    }
    if (phone && !/^(0|\+84)(\d{9})$/.test(phone)) {
      setValidationError(t("phoneInvalid"));
      return;
    }

    setValidationError(null);

    const pendingFile = pendingFileRef.current;
    if (pendingFile && avatarObjectUrl) {
      uploadAvatarMutate(pendingFile, {
        onSuccess: (response) => {
          if (response.success && response.data) {
            pendingFileRef.current = null;
            persistProfile(response.data.avatarUrl);
          } else {
            toast.error(response.message || t("saveError"));
          }
        },
        onError: () => toast.error(t("saveError")),
      });
      return;
    }

    persistProfile(profile.avatarUrl ?? null);
  };

  const disabled = isUpdating || isUploadingAvatar;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-4">
        <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">
          {t("formTitle")}
        </h2>
        <div className="flex flex-col sm:flex-row items-center gap-6">
          <AvatarDropzone
            preview={avatarPreview}
            fullName={fullName}
            disabled={disabled}
            onFileSelected={handleAvatarPicked}
          />
          <div className="text-center sm:text-left">
            <h3 className="text-base font-bold text-black dark:text-white">{t("avatarTitle")}</h3>
            <p className="text-xs text-neutral-400 mt-1">{t("avatarHelp")}</p>
          </div>
        </div>
      </div>

      {validationError && (
        <div
          role="alert"
          className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200"
        >
          {validationError}
        </div>
      )}

      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="username">{t("username")}</Label>
          <Input
            id="username"
            type="text"
            value={profile.username || authUsername || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email">{t("email")}</Label>
          <Input
            id="email"
            type="text"
            value={profile.email || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="role">{t("role")}</Label>
          <Input
            id="role"
            type="text"
            value={profile.role || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="fullName">{t("fullName")}</Label>
          <Input
            id="fullName"
            type="text"
            disabled={disabled}
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            placeholder={t("fullNamePlaceholder")}
            required
            maxLength={50}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="phone">{t("phone")}</Label>
          <Input
            id="phone"
            type="text"
            disabled={disabled}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder={t("phonePlaceholder")}
          />
        </div>
      </div>

      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={disabled}
        className="w-full justify-center h-10 font-bold mt-2"
      >
        {disabled ? (
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
            {t("saving")}
          </span>
        ) : (
          t("save")
        )}
      </Button>
    </form>
  );
}

function ProfileFormFields(props: { profile: ProfileData; authUsername: string | null }) {
  return <ProfileFormFieldsImpl {...props} />;
}

const MemoizedProfileFormFields = memo(ProfileFormFields);

function ProfileFormImpl() {
  const t = useTranslations("profile");
  const { data: profileResponse, isLoading, isError, refetch } = useProfile();
  const authUser = useAuthStore((state) => state.user);

  if (isLoading) return <ProfileFormSkeleton />;
  if (isError) return <ProfileFormError t={t} onRetry={() => refetch()} />;
  if (!profileResponse?.data) return null;

  return (
    <MemoizedProfileFormFields
      key={profileResponse.data.email ?? "empty"}
      profile={profileResponse.data}
      authUsername={authUser?.username ?? null}
    />
  );
}

export const ProfileForm = memo(ProfileFormImpl);
