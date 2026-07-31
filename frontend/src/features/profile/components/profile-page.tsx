"use client";

import { useState, useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { useProfile, useUpdateProfile, useUploadAvatar } from "../api/profile";

const ALLOWED_AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_AVATAR_SIZE = 5 * 1024 * 1024;

export function ProfilePage() {
  const t = useTranslations("profile");
  const tCommon = useTranslations("common");

  const { data, isLoading, error, refetch } = useProfile();
  const updateProfileMutation = useUpdateProfile({
    mutationConfig: {
      onSuccess: () => {
        toast.success(t("saveSuccess"));
      },
      onError: () => {
        toast.error(t("saveError"));
      },
    },
  });
  const uploadAvatarMutation = useUploadAvatar({
    mutationConfig: {
      onSuccess: () => {
        toast.success(t("saveSuccess"));
      },
      onError: () => {
        toast.error(t("saveError"));
      },
    },
  });

  const [fullName, setFullName] = useState("");
  const [isEditing, setIsEditing] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const profile = data?.data;

  useEffect(() => {
    if (profile?.fullName != null) {
      setFullName(profile.fullName);
    }
  }, [profile]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-4">
        <p className="text-neutral-500">{t("loadError")}</p>
        <Button onClick={() => refetch()} variant="outline">
          {t("retry")}
        </Button>
      </div>
    );
  }

  const handleSave = () => {
    if (!fullName.trim()) {
      toast.error(t("fullNameRequired"));
      return;
    }
    updateProfileMutation.mutate(
      { data: { fullName: fullName.trim() } },
      {
        onSuccess: () => setIsEditing(false),
      }
    );
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setAvatarError(null);
    const file = e.target.files?.[0];
    if (!file) return;

    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      setAvatarError(t("avatarTypeError"));
      return;
    }

    if (file.size > MAX_AVATAR_SIZE) {
      setAvatarError(t("avatarSizeError"));
      return;
    }

    uploadAvatarMutation.mutate(file, {
      onSuccess: () => {
        toast.success(t("saveSuccess"));
      },
      onError: () => {
        toast.error(t("saveError"));
      },
    });
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const getRoleLabel = (role: string | null | undefined) => {
    if (!role) return "-";
    const roleMap: Record<string, string> = {
      ADMIN: "Admin",
      PRO: "Pro",
      USER: "User",
    };
    return roleMap[role] ?? role;
  };

  const getRoleBorderColor = (role: string | null | undefined) => {
    if (!role) return "border-neutral-300 dark:border-neutral-600";
    const borderMap: Record<string, string> = {
      ADMIN: "border-red-500",
      PRO: "border-amber-500",
      USER: "border-blue-500",
    };
    return borderMap[role] ?? "border-neutral-300 dark:border-neutral-600";
  };

  const getRoleBgColor = (role: string | null | undefined) => {
    if (!role) return "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300";
    const bgMap: Record<string, string> = {
      ADMIN: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400",
      PRO: "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400",
      USER: "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
    };
    return bgMap[role] ?? "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300";
  };

  return (
    <div className="flex flex-col gap-8 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>

      <div className="flex flex-col gap-6">
        <div className="rounded-lg border border-neutral-200 bg-white p-6 dark:border-neutral-800 dark:bg-neutral-950">
          <h2 className="mb-6 text-lg font-semibold text-black dark:text-white">
            {t("formTitle")}
          </h2>

          <div className="flex flex-col gap-6 sm:flex-row">
            <div className="flex flex-col items-center gap-4">
              <div className="flex flex-col items-center gap-2">
                <span className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-medium ${getRoleBgColor(profile.role)}`}>
                  {getRoleLabel(profile.role)}
                </span>
                <div className={`relative rounded-full ring-2 ring-offset-2 ring-offset-white dark:ring-offset-neutral-950 ${getRoleBorderColor(profile.role)}`}>
                  {profile.avatarUrl ? (
                    <img
                      src={profile.avatarUrl}
                      alt="Avatar"
                      className="h-24 w-24 rounded-full object-cover"
                    />
                  ) : (
                    <div className="flex h-24 w-24 items-center justify-center rounded-full bg-neutral-200 text-2xl font-bold text-neutral-500 dark:bg-neutral-800 dark:text-neutral-400">
                      {profile.fullName?.charAt(0)?.toUpperCase() ?? "?"}
                    </div>
                  )}
                  {uploadAvatarMutation.isPending && (
                    <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50">
                      <Spinner className="h-6 w-6 text-white" />
                    </div>
                  )}
                </div>
              </div>

              <div className="flex flex-col items-center gap-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={ALLOWED_AVATAR_TYPES.join(",")}
                  onChange={handleAvatarChange}
                  className="hidden"
                  id="avatar-upload"
                />
                <label
                  htmlFor="avatar-upload"
                  className="cursor-pointer text-sm font-medium text-neutral-600 underline hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
                >
                  {t("avatarTitle")}
                </label>
                {avatarError && (
                  <p className="text-center text-xs text-red-500">{avatarError}</p>
                )}
              </div>
            </div>

            <div className="flex flex-1 flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                    {t("email")}
                  </label>
                  <Input
                    value={profile.email ?? "-"}
                    disabled
                    className="bg-neutral-50 dark:bg-neutral-900"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  {t("fullName")}
                </label>
                <div className="flex gap-2">
                  <Input
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    onFocus={() => setIsEditing(true)}
                    placeholder={t("fullNamePlaceholder")}
                    maxLength={128}
                    className="flex-1"
                  />
                  {isEditing && (
                    <>
                      <Button
                        variant="outline"
                        onClick={() => {
                          setFullName(profile.fullName ?? "");
                          setIsEditing(false);
                          setAvatarError(null);
                        }}
                        disabled={updateProfileMutation.isPending}
                      >
                        {tCommon("cancel")}
                      </Button>
                      <Button
                        onClick={handleSave}
                        disabled={updateProfileMutation.isPending}
                      >
                        {updateProfileMutation.isPending
                          ? t("saving")
                          : tCommon("save")}
                      </Button>
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
