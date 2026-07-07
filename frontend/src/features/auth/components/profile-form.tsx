"use client";

import { useState, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { useProfile, useUpdateProfile } from "../api/profile";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export function ProfileForm() {
  const t = useTranslations("profile");
  const { data: profileResponse, isLoading } = useProfile();
  const { mutate: updateProfileMutate, isPending } = useUpdateProfile();

  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (profileResponse?.data) {
      setFullName(profileResponse.data.fullName || "");
      setPhone(profileResponse.data.phone || "");
      setAvatarPreview(profileResponse.data.avatarUrl);
    }
  }, [profileResponse]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // File validation
    if (file.size > 2 * 1024 * 1024) {
      toast.error(t("avatarSizeError"));
      return;
    }

    if (!["image/jpeg", "image/jpg", "image/png"].includes(file.type)) {
      toast.error(t("avatarTypeError"));
      return;
    }

    // Set local preview
    const objectUrl = URL.createObjectURL(file);
    setAvatarPreview(objectUrl);
    toast.success(t("avatarSelected"));
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      toast.error(t("avatarSizeError"));
      return;
    }

    if (!["image/jpeg", "image/jpg", "image/png"].includes(file.type)) {
      toast.error(t("avatarTypeError"));
      return;
    }

    const objectUrl = URL.createObjectURL(file);
    setAvatarPreview(objectUrl);
    toast.success(t("avatarDropped"));
  };

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

    const originalAvatarUrl = profileResponse?.data?.avatarUrl || null;

    updateProfileMutate(
      {
        data: {
          fullName,
          phone: phone || null,
          avatarUrl: originalAvatarUrl,
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
        onError: (err: any) => {
          setValidationError(err.message || t("saveError"));
          toast.error(t("saveError"));
        },
      }
    );
  };

  if (isLoading) {
    // Shimmer Loading Skeleton
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

  const profile = profileResponse?.data;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6 font-sans">
      {/* Header Info */}
      <div className="flex flex-col gap-4">
        <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">
          {t("formTitle")}
        </h2>
        <div className="flex flex-col sm:flex-row items-center gap-6">
          {/* Avatar Upload Drop Zone */}
          <div
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className="group relative size-20 cursor-pointer rounded-full border-2 border-dashed border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-900 flex items-center justify-center overflow-hidden hover:border-black dark:hover:border-white transition-colors"
            title="Kéo thả hoặc click để thay đổi avatar"
          >
            {avatarPreview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={avatarPreview}
                alt="Avatar"
                className="size-full object-cover group-hover:opacity-75 transition-opacity"
              />
            ) : (
              <span className="text-2xl font-bold text-neutral-400">
                {fullName ? fullName.charAt(0).toUpperCase() : "?"}
              </span>
            )}
            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <svg className="size-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
          </div>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept=".jpg,.jpeg,.png"
            className="hidden"
          />
          <div className="text-center sm:text-left">
            <h3 className="text-base font-bold text-black dark:text-white">{t("avatarTitle")}</h3>
            <p className="text-xs text-neutral-400 mt-1">
              {t("avatarHelp")}
            </p>
          </div>
        </div>
      </div>

      {validationError && (
        <div role="alert" className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200">
          {validationError}
        </div>
      )}

      {/* Inputs */}
      <div className="flex flex-col gap-4">
        {/* Username */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="username">{t("username")}</Label>
          <Input
            id="username"
            type="text"
            value={profile?.username || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        {/* Email */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email">{t("email")}</Label>
          <Input
            id="email"
            type="text"
            value={profile?.email || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        {/* Role */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="role">{t("role")}</Label>
          <Input
            id="role"
            type="text"
            value={profile?.role || ""}
            disabled
            className="bg-neutral-50 dark:bg-neutral-950 cursor-not-allowed border-neutral-200 dark:border-neutral-800 text-neutral-500"
            aria-readonly="true"
          />
        </div>

        {/* Full Name */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="fullName">{t("fullName")}</Label>
          <Input
            id="fullName"
            type="text"
            disabled={isPending}
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            placeholder={t("fullNamePlaceholder")}
            required
            maxLength={50}
          />
        </div>

        {/* Phone */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="phone">{t("phone")}</Label>
          <Input
            id="phone"
            type="text"
            disabled={isPending}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder={t("phonePlaceholder")}
          />
        </div>
      </div>

      {/* Action Button */}
      <Button
        type="submit"
        variant="default"
        size="lg"
        disabled={isPending}
        className="w-full justify-center h-10 font-bold mt-2"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <svg className="animate-spin size-4 text-white dark:text-black" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
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
