"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { useCreateVoiceTag } from "@/features/audio";

const LANGUAGES: { code: string; label: string; voices: string[] }[] = [
  {
    code: "vi-VN",
    label: "Tiếng Việt",
    voices: ["vi-VN-Neural2-A", "vi-VN-Standard-A", "vi-VN-Wavenet-A"],
  },
  {
    code: "en-US",
    label: "English (US)",
    voices: ["en-US-Neural2-A", "en-US-Neural2-D", "en-US-Standard-A", "en-US-Wavenet-A"],
  },
];

interface CreateVoiceTagModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateVoiceTagModal({ open, onOpenChange }: CreateVoiceTagModalProps) {
  const t = useTranslations("dashboard.modals");
  const tCommon = useTranslations("dashboard.common");
  const [text, setText] = useState("");
  const [languageCode, setLanguageCode] = useState<string>(LANGUAGES[0].code);
  const [voiceName, setVoiceName] = useState<string>(LANGUAGES[0].voices[0]);
  const [error, setError] = useState<string | null>(null);

  const { mutate: createMutate, isPending } = useCreateVoiceTag();

  const selectedLanguage =
    LANGUAGES.find((l) => l.code === languageCode) ?? LANGUAGES[0];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = text.trim();
    if (trimmed.length < 1 || trimmed.length > 250) {
      setError(tCommon("error"));
      return;
    }
    createMutate(
      {
        data: {
          textContent: trimmed,
          languageCode,
          voiceName,
        },
      },
      {
        onSuccess: (res) => {
          if (res.success) {
            toast.success(t("createTagSuccess"));
            setText("");
            onOpenChange(false);
          } else {
            setError(res.message || tCommon("error"));
          }
        },
        onError: asApiError((err) => {
          setError(err.message || tCommon("error"));
        }),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !isPending && onOpenChange(o)}>
      {open ? (
        <DialogContent className="sm:max-w-md" showCloseButton={!isPending}>
          <DialogHeader>
            <DialogTitle>{t("createTagTitle")}</DialogTitle>
            <DialogDescription>{t("createTagDesc")}</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="tag-text">{t("createTagTextLabel")}</Label>
              <textarea
                id="tag-text"
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder={t("createTagTextPlaceholder")}
                maxLength={250}
                rows={4}
                className="w-full rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                required
              />
              <span className="self-end text-xs text-neutral-500">
                {text.length}/250
              </span>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-2">
                <Label htmlFor="tag-language">{t("createTagLanguageLabel")}</Label>
                <select
                  id="tag-language"
                  value={languageCode}
                  onChange={(e) => {
                    const newCode = e.target.value;
                    setLanguageCode(newCode);
                    const found = LANGUAGES.find((l) => l.code === newCode);
                    if (found) setVoiceName(found.voices[0]);
                  }}
                  className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                >
                  {LANGUAGES.map((lang) => (
                    <option key={lang.code} value={lang.code}>
                      {lang.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="tag-voice">{t("createTagVoiceLabel")}</Label>
                <select
                  id="tag-voice"
                  value={voiceName}
                  onChange={(e) => setVoiceName(e.target.value)}
                  className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                >
                  {selectedLanguage.voices.map((voice) => (
                    <option key={voice} value={voice}>
                      {voice}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {error && (
              <div
                role="alert"
                className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/30 dark:text-red-400"
              >
                {error}
              </div>
            )}

            <DialogFooter className="-mx-4 -mb-4">
              <Button
                type="button"
                variant="ghost"
                disabled={isPending}
                onClick={() => onOpenChange(false)}
              >
                {t("closeBtn")}
              </Button>
              <Button type="submit" disabled={isPending || text.trim().length === 0}>
                {isPending ? tCommon("loading") : t("createTagSubmit")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
