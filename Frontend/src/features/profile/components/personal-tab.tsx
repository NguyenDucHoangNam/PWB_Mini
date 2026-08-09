"use client";

import { Loader2 } from "lucide-react";

export interface PersonalTabProps {
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

export function PersonalTab({
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
    <div role="tabpanel" className="neu-raised space-y-6 rounded-3xl bg-[#e0e5ec] p-6 sm:p-8 dark:bg-[#1e222b]">
      <FieldGroup label={labels.email}>
        <input
          value={email ?? "-"}
          disabled
          readOnly
          className="neu-pressed w-full rounded-2xl bg-[#e0e5ec] px-4 py-3 text-sm font-medium text-slate-500 dark:bg-[#1e222b] dark:text-slate-400 cursor-not-allowed select-none border-none outline-none"
        />
      </FieldGroup>

      <FieldGroup label={labels.fullName}>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <input
            value={fullName}
            onChange={(e) => onChangeFullName(e.target.value)}
            onFocus={onFocusFullName}
            placeholder={labels.placeholder}
            maxLength={128}
            className="neu-input w-full flex-1 rounded-2xl bg-[#e0e5ec] px-4 py-3 text-sm font-medium text-slate-900 placeholder:text-slate-400 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:bg-[#1e222b] dark:text-slate-100 border-none"
            aria-label={labels.fullName}
          />
          {isEditing && (
            <div className="flex items-center gap-3 shrink-0">
              <button
                type="button"
                onClick={onCancel}
                disabled={isSaving}
                className="neu-button rounded-2xl px-5 py-3 text-sm font-semibold text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 disabled:opacity-50 dark:text-slate-200"
              >
                {labels.cancel}
              </button>
              <button
                type="button"
                onClick={onSave}
                disabled={isSaving}
                className="neu-button-primary flex items-center justify-center gap-2 rounded-2xl bg-indigo-600 px-6 py-3 text-sm font-bold text-white shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 disabled:opacity-50"
              >
                {isSaving ? (
                  <>
                    <Loader2 className="size-4 animate-spin" />
                    <span>{labels.saving}</span>
                  </>
                ) : (
                  labels.save
                )}
              </button>
            </div>
          )}
        </div>
      </FieldGroup>
    </div>
  );
}

export interface FieldGroupProps {
  label: string;
  children: React.ReactNode;
}

export function FieldGroup({ label, children }: FieldGroupProps) {
  return (
    <div className="flex flex-col gap-2">
      <label className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
        {label}
      </label>
      {children}
    </div>
  );
}
