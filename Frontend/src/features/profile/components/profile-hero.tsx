"use client";

import { useId } from "react";
import { Camera, Crown, Loader2, Lock, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ALLOWED_AVATAR_TYPES } from "../constants";

export interface ProfileHeroProps {
  email: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  roleLabel: string;
  role?: string | null;
  avatarTitle: string;
  onGoSecurity: () => void;
  onAvatarChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onClearAvatarError: () => void;
  isUploading: boolean;
  /** 0-100 while bytes are in flight; 100 also covers the server-side wait after the last byte. */
  uploadPercent: number;
  avatarError: string | null;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  changePasswordLabel: string;
}

export function ProfileHero({
  email,
  fullName,
  avatarUrl,
  roleLabel,
  role,
  avatarTitle,
  onGoSecurity,
  onAvatarChange,
  onClearAvatarError,
  isUploading,
  uploadPercent,
  avatarError,
  fileInputRef,
  changePasswordLabel,
}: ProfileHeroProps) {
  const uploadId = useId();
  const fallbackInitial =
    fullName?.trim().charAt(0).toUpperCase() ?? email?.charAt(0).toUpperCase() ?? "?";
  const resolvedFullName = fullName?.trim() || email || "—";
  const isPro = role === "PRO" || roleLabel === "PRO";

  return (
    <section className="relative overflow-hidden rounded-2xl border border-neutral-200 bg-gradient-to-br from-white via-white to-neutral-50 p-6 shadow-sm ring-1 ring-black/5 sm:p-8 dark:border-neutral-800 dark:from-neutral-950 dark:via-neutral-950 dark:to-neutral-900 dark:ring-white/5">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-neutral-300 to-transparent dark:via-neutral-700" />
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:gap-8">
        <div className="relative shrink-0">
          <div
            className={cn(
              "relative flex size-24 items-center justify-center rounded-full p-1 transition-all duration-200 sm:size-28",
              isPro
                ? "bg-gradient-to-tr from-amber-500 via-amber-300 to-yellow-400 shadow-xl shadow-amber-500/25"
                : "bg-neutral-100 ring-2 ring-neutral-200 dark:bg-neutral-800 dark:ring-neutral-700",
            )}
          >
            <div className="flex size-full items-center justify-center overflow-hidden rounded-full bg-white text-2xl font-bold text-neutral-600 dark:bg-neutral-900 dark:text-neutral-200">
              {avatarUrl ? (
                <img src={avatarUrl} alt={resolvedFullName} className="h-full w-full object-cover" />
              ) : (
                fallbackInitial
              )}
            </div>
            {isPro && (
              <span className="absolute top-0 right-0 z-10 flex size-7 items-center justify-center rounded-full bg-gradient-to-tr from-amber-400 to-yellow-500 text-black shadow-md ring-2 ring-white dark:ring-neutral-950 sm:size-8">
                <Crown className="size-4 fill-black stroke-black" />
              </span>
            )}
          </div>
          {isUploading && (
            <div
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              // Indeterminate once the bytes are sent: the server-side wait has no honest number.
              aria-valuenow={uploadPercent < 100 ? uploadPercent : undefined}
              className="absolute inset-0 flex items-center justify-center rounded-full bg-black/55 backdrop-blur-sm"
              style={{
                backgroundImage: `conic-gradient(rgba(255,255,255,0.45) ${uploadPercent * 3.6}deg, transparent 0deg)`,
              }}
            >
              {uploadPercent < 100 ? (
                <span className="text-sm font-semibold tabular-nums text-white">{uploadPercent}%</span>
              ) : (
                <Loader2 className="size-7 animate-spin text-white" />
              )}
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
            className="key-press absolute -bottom-1 -right-1 flex size-9 cursor-pointer items-center justify-center rounded-full border-2 border-white bg-black text-white shadow-lg hover:scale-105 dark:border-neutral-950 dark:bg-white dark:text-black"
            aria-label={avatarTitle}
          >
            <Camera className="size-4" aria-hidden="true" />
          </label>
        </div>

        <div className="min-w-0 flex-1">
          {isPro ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-gradient-to-r from-amber-500 to-yellow-500 px-3 py-1 text-xs font-bold uppercase tracking-wider text-black shadow-sm shadow-amber-500/20">
              <Crown className="size-3.5 fill-black stroke-black" />
              {roleLabel}
            </span>
          ) : (
            <span className="inline-flex items-center rounded-full bg-neutral-900 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-50 dark:bg-neutral-50 dark:text-neutral-900">
              {roleLabel}
            </span>
          )}
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
