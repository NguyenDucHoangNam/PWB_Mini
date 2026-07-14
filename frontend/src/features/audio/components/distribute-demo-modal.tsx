"use client";

import { useEffect, useState } from "react";
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
import { Checkbox } from "@/components/ui/checkbox";
import { asApiError } from "@/lib/api-client";
import { suggestRecipients, useDistributeDemo } from "@/features/audio";
import type { DistributeDemoResponse } from "@/features/audio/types";

interface DistributeDemoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  demoId: string;
  demoTitle?: string;
}

export function DistributeDemoModal({
  open,
  onOpenChange,
  demoId,
  demoTitle,
}: DistributeDemoModalProps) {
  const t = useTranslations("dashboard.modals");
  const tCommon = useTranslations("dashboard.common");
  const [email, setEmail] = useState("");
  const [allowDownload, setAllowDownload] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DistributeDemoResponse | null>(null);
  const [suggestions, setSuggestions] = useState<string[]>([]);

  const { mutate: distributeMutate, isPending } = useDistributeDemo();

  useEffect(() => {
    if (!open) {
      setEmail("");
      setAllowDownload(true);
      setError(null);
      setResult(null);
      setSuggestions([]);
    }
  }, [open]);

  useEffect(() => {
    const trimmed = email.trim();
    if (trimmed.length < 2 || trimmed.length > 50) {
      setSuggestions([]);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const res = await suggestRecipients({ q: trimmed });
        if (controller.signal.aborted) return;
        if (res.success && res.data) setSuggestions(res.data);
      } catch {
        // ignore - autocomplete is best effort
      }
    }, 300);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [email]);

  const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!isValidEmail) {
      setError(tCommon("error"));
      return;
    }
    distributeMutate(
      {
        demoId,
        data: {
          recipientEmail: email.trim(),
          allowDownload,
        },
      },
      {
        onSuccess: (res) => {
          if (res.success && res.data) {
            setResult(res.data);
            toast.success(t("shareSuccess"));
            setEmail("");
            setSuggestions([]);
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

  const handleCopy = async (link: string) => {
    try {
      await navigator.clipboard.writeText(link);
      toast.success(t("shareResultCopied"));
    } catch {
      toast.error(tCommon("error"));
    }
  };

  const handleNewShare = () => {
    setResult(null);
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !isPending && onOpenChange(o)}>
      {open ? (
        <DialogContent className="sm:max-w-lg" showCloseButton={!isPending}>
          <DialogHeader>
            <DialogTitle>{t("shareTitle")}</DialogTitle>
            <DialogDescription>
              {demoTitle ? `${demoTitle} - ` : ""}
              {t("shareDesc")}
            </DialogDescription>
          </DialogHeader>

          {!result ? (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="share-email">{t("shareEmailLabel")}</Label>
                <Input
                  id="share-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t("shareEmailPlaceholder")}
                  maxLength={100}
                  autoComplete="off"
                />
                {suggestions.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {suggestions.slice(0, 5).map((s) => (
                      <button
                        key={s}
                        type="button"
                        onClick={() => setEmail(s)}
                        className="rounded-full border border-neutral-200 bg-neutral-50 px-2.5 py-0.5 text-xs font-medium text-neutral-700 transition-colors hover:bg-neutral-100 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-300 dark:hover:bg-neutral-800"
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex items-center gap-2">
                <Checkbox
                  id="share-allow-download"
                  checked={allowDownload}
                  onCheckedChange={(checked: boolean) => setAllowDownload(checked)}
                />
                <Label htmlFor="share-allow-download">{t("shareAllowDownload")}</Label>
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
                <Button type="submit" disabled={isPending || !isValidEmail}>
                  {isPending ? tCommon("loading") : t("shareSubmit")}
                </Button>
              </DialogFooter>
            </form>
          ) : (
            <div className="flex flex-col gap-4">
              <div className="rounded-lg border border-neutral-200 bg-neutral-50 p-4 dark:border-neutral-800 dark:bg-neutral-900/40">
                <div className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
                  {t("shareEmailLabel")}
                </div>
                <div className="mt-1 break-all text-sm font-semibold text-black dark:text-white">
                  {result.recipientEmail}
                </div>
                <div className="mt-3 text-xs font-medium text-neutral-500 dark:text-neutral-400">
                  {t("shareResultTitle")}
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <Input readOnly value={result.shareLink} className="font-mono text-xs" />
                  <Button size="sm" variant="outline" onClick={() => handleCopy(result.shareLink)}>
                    {t("shareResultCopy")}
                  </Button>
                </div>
              </div>

              <DialogFooter className="-mx-4 -mb-4">
                <Button variant="ghost" onClick={() => onOpenChange(false)}>
                  {t("closeBtn")}
                </Button>
                <Button onClick={handleNewShare}>{t("shareResultRecentTitle")}</Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
