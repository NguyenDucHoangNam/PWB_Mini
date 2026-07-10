"use client";

import { useState, useEffect } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useDeleteAccount } from "../api/account";
import { useProfile } from "../api/profile";
import { PasswordInput } from "./password-input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "sonner";

interface DeleteAccountDialogProps {
  isOpen: boolean;
  onClose: () => void;
}

export function DeleteAccountDialog({ isOpen, onClose }: DeleteAccountDialogProps) {
  const t = useTranslations("account.delete");
  const router = useRouter();
  const { data: profileResponse } = useProfile({ queryConfig: { enabled: isOpen } });
  const { mutate: deleteAccountMutate, isPending } = useDeleteAccount();

  const [password, setPassword] = useState("");
  const [googleIdToken, setGoogleIdToken] = useState<string | null>(null);
  const [googleReauthSuccess, setGoogleReauthSuccess] = useState(false);
  const [countdown, setCountdown] = useState(3);
  const [error, setError] = useState<string | null>(null);

  // Countdown timer when dialog opens.
  useEffect(() => {
    if (!isOpen) return;
    setCountdown(3);
    setError(null);
    setPassword("");
    setGoogleIdToken(null);
    setGoogleReauthSuccess(false);

    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [isOpen]);

  const oauthProvider = profileResponse?.data?.oauthProvider;
  const isGoogleUser = oauthProvider === "GOOGLE";

  const handleGoogleReauth = () => {
    const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
    if (!clientId || typeof window === "undefined" || !window.google) {
      toast.error("Google Identity Services not available");
      return;
    }
    toast.info(t("googleReauthToast"));
    // For one-shot re-auth we initialize a temporary instance so we
    // don't disturb the login-form's initialization.
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => {
        if (response.credential) {
          setGoogleIdToken(response.credential);
          setGoogleReauthSuccess(true);
          toast.success(t("googleReauthSuccessToast"));
        }
      },
    });
    try {
      window.google.accounts.id.prompt();
    } catch {
      toast.error("Google re-authentication failed");
    }
  };

  const handleDeleteSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!isGoogleUser && !password) {
      setError(t("requiredPassword"));
      return;
    }

    if (isGoogleUser && !googleReauthSuccess) {
      setError(t("requiredGoogle"));
      return;
    }

    setError(null);

    deleteAccountMutate(
      {
        data: isGoogleUser ? { idToken: googleIdToken ?? undefined } : { password },
      },
      {
        onSuccess: (res) => {
          if (res.success) {
            toast.success(t("toastSuccess"));
            onClose();
            router.push("/login");
          } else {
            setError(res.message || t("toastError"));
          }
        },
        onError: (err: any) => {
          const apiError = err?.errors?.[0];
          if (apiError?.code === "INVALID_PASSWORD") {
            setError(t("incorrectPassword"));
          } else if (apiError?.code === "ACCOUNT_TEMPORARILY_LOCKED") {
            setError(t("accountLocked"));
          } else {
            setError(err?.message || t("toastError"));
          }
          toast.error(t("toastToast") || t("toastError"));
        },
      },
    );
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("warningDesc")}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleDeleteSubmit} className="flex flex-col gap-5">
          <div className="rounded-lg bg-neutral-50 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 p-4 text-xs leading-relaxed text-neutral-500">
            <strong className="text-black dark:text-white flex items-center gap-1.5 mb-1.5">
              <svg
                className="size-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
              {t("warningTitle")}
            </strong>
          </div>

          {error && (
            <div
              role="alert"
              className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200"
            >
              {error}
            </div>
          )}

          {isGoogleUser ? (
            <div className="flex flex-col gap-2">
              <Label>{t("googleReauth")}</Label>
              <Button
                type="button"
                variant="outline"
                onClick={handleGoogleReauth}
                disabled={countdown > 0 || googleReauthSuccess || isPending}
                className="w-full justify-center gap-2"
              >
                <svg className="size-4" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12.24 10.285V14.4h6.887c-.648 2.41-2.519 4.114-5.136 4.114-3.524 0-6.386-2.862-6.386-6.386 0-3.524 2.862-6.386 6.386-6.386 1.63 0 3.116.618 4.256 1.63l3.056-3.056C19.34 2.502 16.035 1 12.24 1 6.136 1 1.18 5.956 1.18 12.06c0 6.104 4.956 11.06 11.06 11.06 6.368 0 11.06-4.475 11.06-11.06 0-.745-.074-1.463-.207-2.149H12.24z" />
                </svg>
                {t("googleReauthButton")}
              </Button>
              {googleReauthSuccess && (
                <span className="text-xs font-semibold text-neutral-600 dark:text-neutral-300">
                  {t("googleReauthSuccess")}
                </span>
              )}
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              <Label htmlFor="deletePassword">{t("passwordLabel")}</Label>
              <PasswordInput
                id="deletePassword"
                disabled={countdown > 0 || isPending}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t("passwordPlaceholder")}
                required
                autoComplete="current-password"
              />
            </div>
          )}

          <div className="flex flex-col gap-2">
            <Button
              type="submit"
              variant="default"
              size="lg"
              disabled={
                countdown > 0 ||
                isPending ||
                (!isGoogleUser && !password) ||
                (isGoogleUser && !googleReauthSuccess)
              }
              className="w-full justify-center h-10 font-bold bg-neutral-900 hover:bg-neutral-800 text-white dark:bg-neutral-100 dark:hover:bg-neutral-200 dark:text-black"
            >
              {isPending ? (
                <span className="flex items-center gap-2">
                  <svg className="animate-spin size-4" fill="none" viewBox="0 0 24 24">
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  {t("submitting")}
                </span>
              ) : (
                t("confirmButton")
              )}
            </Button>
            {countdown > 0 && (
              <span className="text-center text-xs text-neutral-400 font-medium">
                {t("countdownText", { seconds: countdown })}
              </span>
            )}
          </div>
        </form>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            disabled={isPending}
            className="w-full justify-center text-xs font-semibold text-neutral-500 hover:text-black dark:hover:text-white"
          >
            {t("cancel")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
