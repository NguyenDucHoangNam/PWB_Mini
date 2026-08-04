"use client";

import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

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
    <div role="tabpanel" className="space-y-5 p-6 sm:p-8">
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
  );
}

export interface FieldGroupProps {
  label: string;
  children: React.ReactNode;
}

export function FieldGroup({ label, children }: FieldGroupProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
        {label}
      </label>
      {children}
    </div>
  );
}
