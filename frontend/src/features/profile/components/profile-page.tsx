"use client";

import { useState, useRef, useEffect, useId } from "react";
import { useTranslations } from "next-intl";
import { Camera, Loader2, Lock, Mail, ShieldCheck, User as UserIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { useProfile, useUpdateProfile, useUploadAvatar } from "../api/profile";
import { PasswordInput } from "@/features/auth/components/password-input";
import { PasswordStrengthBar } from "@/features/auth/components/password-strength-bar";
import { PasswordRules } from "@/features/auth/components/password-rules";
import { useChangePassword } from "@/features/auth/api/change-password";
import { usePasswordStrength } from "@/features/auth/hooks/use-password-strength";
import { useRetryCountdown } from "@/features/auth/hooks/use-retry-countdown";
import {
  PASSWORD_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
} from "@/features/auth/hooks/password-validators";
import { asApiError } from "@/lib/api-client";

const ALLOWED_AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_AVATAR_SIZE = 5 * 1024 * 1024;

type ProfileTabId = "personal" | "security";

interface ProfileTab {
  id: ProfileTabId;
  icon: React.ComponentType<{ className?: string }>;
  labelKey: "personal" | "security";
}

const PROFILE_TABS: readonly ProfileTab[] = [
  { id: "personal", icon: UserIcon, labelKey: "personal" },
  { id: "security", icon: ShieldCheck, labelKey: "security" },
] as const;

const ROLE_LABEL_KEYS: Record<string, string> = {
  ADMIN: "roleValue.ADMIN",
  PRO: "roleValue.PRO",
  USER: "roleValue.USER",
};

function useProfileHashTab() {
  const [tab, setTab] = useState<ProfileTabId>("personal");
  useEffect(() => {
    if (typeof window === "undefined") return;
    const applyFromHash = () => {
      const next = window.location.hash.replace(/^#/, "");
      if (next === "security" || next === "personal") {
        setTab(next);
      }
    };
    applyFromHash();
    window.addEventListener("hashchange", applyFromHash);
    return () => window.removeEventListener("hashchange", applyFromHash);
  }, []);
  const setActiveTab = (next: ProfileTabId) => {
    setTab(next);
    if (typeof window !== "undefined") {
      const target = `#${next}`;
      if (window.location.hash !== target) {
        history.replaceState(null, "", target);
      }
    }
  };
  return [tab, setActiveTab] as const;
}

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

  const [fullName, setFullName] = useState("");
  const [isEditing, setIsEditing] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [isOauthOnly, setIsOauthOnly] = useState(false);
  const retryCountdown = useRetryCountdown();
  const strength = usePasswordStrength(newPassword);
  const changePasswordMutation = useChangePassword();

  const profile = data?.data;

  useEffect(() => {
    if (profile?.fullName != null) {
      setFullName(profile.fullName);
    }
  }, [profile]);

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
        onSuccess: () => setIsEditing(false),
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
          const apiError = err.errors?.[0];
          if (apiError?.code === "AUTH_OAUTH_USER_NO_PASSWORD") {
            setIsOauthOnly(true);
          } else if (apiError?.code === "AUTH_INVALID_CURRENT_PASSWORD") {
            setPasswordError(tChangePassword("incorrectOld"));
          } else if (apiError?.code === "AUTH_PASSWORD_REUSED") {
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
              setFullName(value);
              if (!isEditing) setIsEditing(true);
            }}
            onFocusFullName={() => {
              if (!isEditing) setIsEditing(true);
            }}
            onSave={handleSave}
            onCancel={() => {
              setFullName(profile.fullName ?? "");
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

interface ProfileHeroProps {
  email: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  roleLabel: string;
  avatarTitle: string;
  onGoSecurity: () => void;
  onAvatarChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onClearAvatarError: () => void;
  isUploading: boolean;
  avatarError: string | null;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  changePasswordLabel: string;
}

function ProfileHero({
  email,
  fullName,
  avatarUrl,
  roleLabel,
  avatarTitle,
  onGoSecurity,
  onAvatarChange,
  onClearAvatarError,
  isUploading,
  avatarError,
  fileInputRef,
  changePasswordLabel,
}: ProfileHeroProps) {
  const uploadId = useId();
  const fallbackInitial =
    fullName?.trim().charAt(0).toUpperCase() ?? email?.charAt(0).toUpperCase() ?? "?";
  const resolvedFullName = fullName?.trim() || email || "—";

  return (
    <section className="relative overflow-hidden rounded-2xl border border-neutral-200 bg-gradient-to-br from-white via-white to-neutral-50 p-6 shadow-sm ring-1 ring-black/5 sm:p-8 dark:border-neutral-800 dark:from-neutral-950 dark:via-neutral-950 dark:to-neutral-900 dark:ring-white/5">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-neutral-300 to-transparent dark:via-neutral-700" />
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:gap-8">
        <div className="relative shrink-0">
          <div className="flex size-24 items-center justify-center overflow-hidden rounded-full bg-neutral-100 text-2xl font-bold text-neutral-600 ring-2 ring-neutral-200 dark:bg-neutral-800 dark:text-neutral-200 dark:ring-neutral-700 sm:size-28">
            {avatarUrl ? (
              <img src={avatarUrl} alt={resolvedFullName} className="h-full w-full object-cover" />
            ) : (
              fallbackInitial
            )}
          </div>
          {isUploading && (
            <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/55 backdrop-blur-sm">
              <Loader2 className="size-7 animate-spin text-white" />
            </div>
          )}
          <input
            ref={fileInputRef}
            type="file"
            accept={ALLOWED_AVATAR_TYPES.join(",")}
            onChange={(e) => {
              onClearAvatarError();
              onAvatarChange(e);
            }}
            className="hidden"
            id={uploadId}
            aria-hidden="true"
          />
          <label
            htmlFor={uploadId}
            className="absolute -bottom-1 -right-1 flex size-9 cursor-pointer items-center justify-center rounded-full border-2 border-white bg-black text-white shadow-lg transition-transform hover:scale-105 active:scale-95 dark:border-neutral-950 dark:bg-white dark:text-black"
            aria-label={avatarTitle}
          >
            <Camera className="size-4" aria-hidden="true" />
          </label>
        </div>

        <div className="min-w-0 flex-1">
          <span className="inline-flex items-center rounded-full bg-neutral-900 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-50 dark:bg-neutral-50 dark:text-neutral-900">
            {roleLabel}
          </span>
          <h1 className="mt-3 truncate text-2xl font-bold tracking-tight text-neutral-900 sm:text-3xl dark:text-neutral-50">
            {resolvedFullName}
          </h1>
          {email && (
            <div className="mt-1.5 flex items-center gap-1.5 text-sm text-neutral-500 dark:text-neutral-400">
              <Mail className="size-3.5" aria-hidden="true" />
              <span className="truncate">{email}</span>
            </div>
          )}
          {avatarError && <p className="mt-2 text-xs font-medium text-red-500">{avatarError}</p>}
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2 sm:flex-col sm:items-end sm:gap-3">
          <Button variant="outline" onClick={onGoSecurity} className="gap-1.5" type="button">
            <Lock className="size-3.5" aria-hidden="true" />
            {changePasswordLabel}
          </Button>
        </div>
      </div>
    </section>
  );
}

interface ProfileTabsProps {
  activeTab: ProfileTabId;
  onChange: (tab: ProfileTabId) => void;
  labels: { personal: string; security: string };
}

function ProfileTabs({ activeTab, onChange, labels }: ProfileTabsProps) {
  const baseId = useId();

  return (
    <div
      role="tablist"
      aria-orientation="horizontal"
      className="flex gap-1 border-b border-neutral-200 bg-neutral-50/60 px-2 dark:border-neutral-800 dark:bg-neutral-900/40 sm:px-3"
    >
      {PROFILE_TABS.map((tab) => {
        const Icon = tab.icon;
        const tabId = `${baseId}-tab-${tab.id}`;
        const panelId = `${baseId}-panel-${tab.id}`;
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            id={tabId}
            role="tab"
            type="button"
            aria-selected={isActive}
            aria-controls={panelId}
            tabIndex={isActive ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowDown") {
                event.preventDefault();
                const currentIdx = PROFILE_TABS.findIndex((t) => t.id === activeTab);
                const nextIdx = (currentIdx + 1) % PROFILE_TABS.length;
                onChange(PROFILE_TABS[nextIdx].id);
              } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
                event.preventDefault();
                const currentIdx = PROFILE_TABS.findIndex((t) => t.id === activeTab);
                const nextIdx = (currentIdx - 1 + PROFILE_TABS.length) % PROFILE_TABS.length;
                onChange(PROFILE_TABS[nextIdx].id);
              }
            }}
            className={cn(
              "relative flex items-center gap-2 px-3 py-3 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-black focus-visible:ring-offset-2 dark:focus-visible:ring-white dark:focus-visible:ring-offset-neutral-950 sm:px-4",
              isActive
                ? "text-neutral-900 dark:text-neutral-50"
                : "text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200",
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            <span>{labels[tab.labelKey]}</span>
            {isActive && (
              <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-neutral-900 dark:bg-neutral-50 sm:inset-x-4" />
            )}
          </button>
        );
      })}
    </div>
  );
}

interface PersonalTabProps {
  email: string | null;
  fullName: string;
  isEditing: boolean;
  isSaving: boolean;
  onChangeFullName: (value: string) => void;
  onFocusFullName: () => void;
  onSave: () => void;
  onCancel: () => void;
  labels: {
    email: string;
    fullName: string;
    placeholder: string;
    cancel: string;
    save: string;
    saving: string;
  };
}

function PersonalTab({
  email,
  fullName,
  isEditing,
  isSaving,
  onChangeFullName,
  onFocusFullName,
  onSave,
  onCancel,
  labels,
}: PersonalTabProps) {
  return (
    <div role="tabpanel" className="grid gap-6 p-6 sm:p-8 md:grid-cols-3">
      <div className="md:col-span-1">
        <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-50">
          {labels.fullName}
        </h2>
        <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">{labels.placeholder}</p>
      </div>

      <div className="space-y-5 md:col-span-2">
        <FieldGroup label={labels.email}>
          <Input value={email ?? "-"} disabled className="bg-neutral-50 dark:bg-neutral-900" />
        </FieldGroup>

        <FieldGroup label={labels.fullName}>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
            <Input
              value={fullName}
              onChange={(e) => onChangeFullName(e.target.value)}
              onFocus={onFocusFullName}
              placeholder={labels.placeholder}
              maxLength={128}
              className="flex-1"
              aria-label={labels.fullName}
            />
            {isEditing && (
              <div className="flex gap-2">
                <Button variant="outline" onClick={onCancel} disabled={isSaving} type="button">
                  {labels.cancel}
                </Button>
                <Button onClick={onSave} disabled={isSaving} type="button">
                  {isSaving ? (
                    <span className="flex items-center gap-2">
                      <Loader2 className="size-4 animate-spin" />
                      {labels.saving}
                    </span>
                  ) : (
                    labels.save
                  )}
                </Button>
              </div>
            )}
          </div>
        </FieldGroup>
      </div>
    </div>
  );
}

interface FieldGroupProps {
  label: string;
  children: React.ReactNode;
}

function FieldGroup({ label, children }: FieldGroupProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
        {label}
      </label>
      {children}
    </div>
  );
}

interface SecurityTabProps {
  isOauthOnly: boolean;
  oauthOnlyMessage: string;
  passwordError: string | null;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  strength: ReturnType<typeof usePasswordStrength>;
  isSubmitting: boolean;
  isRateLimited: boolean;
  retryRemainingSeconds: number;
  onChangeCurrentPassword: (value: string) => void;
  onChangeNewPassword: (value: string) => void;
  onChangeConfirmPassword: (value: string) => void;
  onBlurConfirmPassword: () => void;
  onSubmit: (e: React.FormEvent) => void;
  labels: {
    oldPassword: string;
    oldPlaceholder: string;
    newPassword: string;
    newPlaceholder: string;
    confirmPassword: string;
    confirmPlaceholder: string;
    submit: string;
    submitting: string;
    retryText: string;
  };
}

function SecurityTab({
  isOauthOnly,
  oauthOnlyMessage,
  passwordError,
  currentPassword,
  newPassword,
  confirmPassword,
  strength,
  isSubmitting,
  isRateLimited,
  onChangeCurrentPassword,
  onChangeNewPassword,
  onChangeConfirmPassword,
  onBlurConfirmPassword,
  onSubmit,
  labels,
}: SecurityTabProps) {
  return (
    <div role="tabpanel" className="p-6 sm:p-8">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200">
          <Lock className="size-5" aria-hidden="true" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-50">
            {labels.oldPassword.replace("*", "").trim()}
          </h2>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{labels.newPassword}</p>
        </div>
      </div>

      {isOauthOnly ? (
        <div className="flex flex-col items-center gap-4 rounded-xl border border-dashed border-neutral-200 bg-neutral-50 py-10 text-center dark:border-neutral-800 dark:bg-neutral-900/40">
          <ShieldCheck className="size-8 text-neutral-400" aria-hidden="true" />
          <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
            {oauthOnlyMessage}
          </p>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4">
          {passwordError && (
            <div className="rounded-lg border border-red-200/60 bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:border-red-900/40 dark:bg-red-950/30 dark:text-red-300">
              {passwordError}
            </div>
          )}

          <FieldGroup label={labels.oldPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={currentPassword}
              onChange={(e) => onChangeCurrentPassword(e.target.value)}
              placeholder={labels.oldPlaceholder}
            />
          </FieldGroup>

          <FieldGroup label={labels.newPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={newPassword}
              onChange={(e) => onChangeNewPassword(e.target.value)}
              placeholder={labels.newPlaceholder}
            />
            <PasswordStrengthBar strength={strength} />
            <PasswordRules password={newPassword} />
          </FieldGroup>

          <FieldGroup label={labels.confirmPassword}>
            <PasswordInput
              disabled={isSubmitting}
              value={confirmPassword}
              onChange={(e) => onChangeConfirmPassword(e.target.value)}
              onBlur={onBlurConfirmPassword}
              placeholder={labels.confirmPlaceholder}
            />
          </FieldGroup>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              disabled={isSubmitting || isRateLimited}
              className="min-w-[140px]"
            >
              {isSubmitting ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="size-4 animate-spin" />
                  {labels.submitting}
                </span>
              ) : isRateLimited ? (
                labels.retryText
              ) : (
                labels.submit
              )}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
