"use client";

import { useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useProfile, useUpdateProfile, useUploadAvatar } from "../api/profile";
import { useChangePassword } from "@/features/auth/api/change-password";
import { usePasswordStrength } from "@/features/auth/hooks/use-password-strength";
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
} from "@/features/auth/hooks/password-validators";
import { useRetryCountdown } from "@/features/auth/hooks/use-retry-countdown";
import { asApiError } from "@/lib/api-client";
import { IamErrorCode } from "@/features/auth/lib/iam-error-codes";
import { ALLOWED_AVATAR_TYPES, MAX_AVATAR_SIZE, ROLE_LABEL_KEYS } from "../constants";
import { useProfileHashTab } from "../hooks/use-profile-hash-tab";
import { ProfileHero } from "./profile-hero";
import { ProfileTabs } from "./profile-tabs";
import { PersonalTab } from "./personal-tab";
import { SecurityTab } from "./security-tab";

export function ProfilePage() {
  const t = useTranslations("profile");
  const tChangePassword = useTranslations("profile.changePassword");
  const tTabs = useTranslations("profile.tabs");
  const tCommon = useTranslations("common");

  const [activeTab, setActiveTab] = useProfileHashTab();

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

  // null means "untouched" — the field then shows whatever the server has. Storing a draft this
  // way, instead of copying the profile into state through an effect, means a background refetch
  // can no longer overwrite what the user is halfway through typing.
  const [draftFullName, setDraftFullName] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  // The server is the authority once it has rejected a change-password attempt; before that the
  // profile already says whether this account has a password at all.
  const [oauthOnlyFromServer, setOauthOnlyFromServer] = useState(false);
  const retryCountdown = useRetryCountdown();
  const strength = usePasswordStrength(newPassword);
  const changePasswordMutation = useChangePassword();

  const profile = data?.data;

  // Derived, not stored: an OAuth-only account is told up front that it has no password rather
  // than discovering it by filling in the form and having the submit rejected.
  const isOauthOnly =
    oauthOnlyFromServer || (!!profile?.oauthProvider && profile.oauthProvider !== "LOCAL");

  const fullName = draftFullName ?? profile?.fullName ?? "";


  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-4">
        <p className="text-sm text-neutral-500">{t("loadError")}</p>
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
        onSuccess: () => {
          setIsEditing(false);
          // Drop the draft so the field tracks the refetched profile again.
          setDraftFullName(null);
        },
      },
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

  const handleChangePassword = (e: React.FormEvent) => {
    e.preventDefault();

    if (!currentPassword || !newPassword || !confirmPassword) {
      setPasswordError(tChangePassword("fillAll"));
      return;
    }

    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      setPasswordError(tChangePassword("minLen"));
      return;
    }

    if (newPassword.length > PASSWORD_MAX_LENGTH) {
      setPasswordError(tChangePassword("maxLen"));
      return;
    }

    if (newPassword === currentPassword) {
      setPasswordError(tChangePassword("reuseError"));
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError(tChangePassword("notMatch"));
      return;
    }

    setPasswordError(null);

    changePasswordMutation.mutate(
      { data: { currentPassword, newPassword } },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(tChangePassword("success"));
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
          } else {
            setPasswordError(response.message || tChangePassword("error"));
          }
        },
        onError: asApiError((err) => {
          if (err.code === IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD) {
            setOauthOnlyFromServer(true);
          } else if (err.code === IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD) {
            setPasswordError(tChangePassword("incorrectOld"));
          } else if (
            err.code === IamErrorCode.AUTH_PASSWORD_REUSED ||
            err.code === IamErrorCode.AUTH_PASSWORD_RECENTLY_USED
          ) {
            setPasswordError(tChangePassword("reuseError"));
          } else if (err.status === 429) {
            retryCountdown.startFromError(err.retryAfterSeconds);
            const msg = err.retryAfterSeconds
              ? tChangePassword("rateLimitErrorWithSeconds", { seconds: err.retryAfterSeconds })
              : tChangePassword("error");
            setPasswordError(msg);
          } else {
            setPasswordError(err.message || tChangePassword("error"));
          }
          toast.error(tChangePassword("error"));
        }),
      },
    );
  };

  const getRoleLabel = (role: string | null | undefined) => {
    if (!role) return "-";
    const key = ROLE_LABEL_KEYS[role];
    if (!key) return role;
    try {
      return t(key as Parameters<typeof t>[0]);
    } catch {
      return role;
    }
  };

  return (
    <div className="flex flex-col gap-8 font-sans">
      <ProfileHero
        email={profile.email ?? null}
        fullName={profile.fullName ?? null}
        avatarUrl={profile.avatarUrl ?? null}
        roleLabel={getRoleLabel(profile.role)}
        role={profile.role}
        avatarTitle={t("avatarTitle")}
        isUploading={uploadAvatarMutation.isPending}
        fileInputRef={fileInputRef}
        onAvatarChange={handleAvatarChange}
        avatarError={avatarError}
        onClearAvatarError={() => setAvatarError(null)}
        onGoSecurity={() => setActiveTab("security")}
        changePasswordLabel={t("changePassword.title")}
      />

      <div className="overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-sm ring-1 ring-black/5 dark:border-neutral-800 dark:bg-neutral-950 dark:ring-white/5">
        <ProfileTabs
          activeTab={activeTab}
          onChange={setActiveTab}
          labels={{
            personal: tTabs("personal"),
            security: tTabs("security"),
          }}
        />

        {activeTab === "personal" ? (
          <PersonalTab
            email={profile.email}
            fullName={fullName}
            isEditing={isEditing}
            isSaving={updateProfileMutation.isPending}
            onChangeFullName={(value) => {
              setDraftFullName(value);
              if (!isEditing) setIsEditing(true);
            }}
            onFocusFullName={() => {
              if (!isEditing) setIsEditing(true);
            }}
            onSave={handleSave}
            onCancel={() => {
              setDraftFullName(null);
              setIsEditing(false);
              setAvatarError(null);
            }}
            labels={{
              email: t("email"),
              fullName: t("fullName"),
              placeholder: t("fullNamePlaceholder"),
              cancel: tCommon("cancel"),
              save: tCommon("save"),
              saving: t("saving"),
            }}
          />
        ) : (
          <SecurityTab
            isOauthOnly={isOauthOnly}
            oauthOnlyMessage={tChangePassword("oauthOnly")}
            passwordError={passwordError}
            currentPassword={currentPassword}
            newPassword={newPassword}
            confirmPassword={confirmPassword}
            strength={strength}
            isSubmitting={changePasswordMutation.isPending}
            isRateLimited={retryCountdown.isActive}
            retryRemainingSeconds={retryCountdown.remaining}
            onChangeCurrentPassword={setCurrentPassword}
            onChangeNewPassword={setNewPassword}
            onChangeConfirmPassword={setConfirmPassword}
            onBlurConfirmPassword={() => {
              if (newPassword && confirmPassword && newPassword !== confirmPassword) {
                setPasswordError(tChangePassword("notMatch"));
              } else if (newPassword && confirmPassword && newPassword === confirmPassword) {
                setPasswordError(null);
              }
            }}
            onSubmit={handleChangePassword}
            labels={{
              oldPassword: tChangePassword("oldPassword"),
              oldPlaceholder: tChangePassword("oldPasswordPlaceholder"),
              newPassword: tChangePassword("newPassword"),
              newPlaceholder: tChangePassword("newPasswordPlaceholder"),
              confirmPassword: tChangePassword("confirmPassword"),
              confirmPlaceholder: tChangePassword("confirmPasswordPlaceholder"),
              submit: tChangePassword("submit"),
              submitting: tChangePassword("submitting"),
              retryText: tChangePassword("retryCountdownText", {
                seconds: retryCountdown.remaining,
              }),
            }}
          />
        )}
      </div>
    </div>
  );
}
