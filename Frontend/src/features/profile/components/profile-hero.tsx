"use client";

import { useId } from "react";
import { Camera, Loader2, Lock, Mail } from "lucide-react";
import { ALLOWED_AVATAR_TYPES } from "../constants";

export interface ProfileHeroProps {
  email: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  roleLabel: string;
  avatarTitle: string;
  onGoSecurity: () => void;
  onAvatarChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onClearAvatarError: () => void;
  isUploading: boolean;
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

  return (
    <section className="neu-raised relative overflow-hidden rounded-3xl bg-[#e0e5ec] p-6 sm:p-8 dark:bg-[#1e222b]">
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:gap-8">
        <div className="relative shrink-0">
          <div className="neu-pressed relative flex size-24 items-center justify-center rounded-full p-2 transition-all sm:size-28">
            <div className="flex size-full items-center justify-center overflow-hidden rounded-full bg-[#e0e5ec] text-2xl font-extrabold text-slate-800 dark:bg-[#1e222b] dark:text-slate-100">
              {avatarUrl ? (
                <img src={avatarUrl} alt={resolvedFullName} className="h-full w-full object-cover" />
              ) : (
                fallbackInitial
              )}
            </div>
          </div>
          {isUploading && (
            <div
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={uploadPercent < 100 ? uploadPercent : undefined}
              className="absolute inset-0 flex items-center justify-center rounded-full bg-slate-900/60 backdrop-blur-xs"
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
            className="neu-button absolute -bottom-1 -right-1 flex size-10 cursor-pointer items-center justify-center rounded-full text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:text-slate-200"
            aria-label={avatarTitle}
          >
            <Camera className="size-4" aria-hidden="true" />
          </label>
        </div>

        <div className="min-w-0 flex-1">
          <span className="neu-pressed-sm inline-flex items-center rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
            {roleLabel}
          </span>
          <h1 className="mt-3 truncate text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl dark:text-slate-50">
            {resolvedFullName}
          </h1>
          {email && (
            <div className="mt-1.5 flex items-center gap-2 text-sm font-medium text-slate-500 dark:text-slate-400">
              <Mail className="size-4 text-slate-400 dark:text-slate-500" aria-hidden="true" />
              <span className="truncate">{email}</span>
            </div>
          )}
          {avatarError && <p className="mt-2 text-xs font-semibold text-rose-500 dark:text-rose-400">{avatarError}</p>}
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2 sm:flex-col sm:items-end sm:gap-3">
          <button
            onClick={onGoSecurity}
            className="neu-button inline-flex items-center justify-center gap-2 rounded-2xl px-4 py-2.5 text-sm font-semibold text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:text-slate-200"
            type="button"
          >
            <Lock className="size-4 text-slate-500 dark:text-slate-400" aria-hidden="true" />
            <span>{changePasswordLabel}</span>
          </button>
        </div>
      </div>
    </section>
  );
}
