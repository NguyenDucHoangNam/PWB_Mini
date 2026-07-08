"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useSessionTimeout } from "@/hooks/use-session-timeout";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export function SessionTimeoutWarning() {
  const t = useTranslations("auth.session");
  const { accessToken } = useAuthStore();
  const { isExpiring, secondsRemaining, extendSession } = useSessionTimeout();
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    if (isExpiring && accessToken) {
      setShowModal(true);
    } else {
      setShowModal(false);
    }
  }, [isExpiring, accessToken]);

  if (!accessToken) return null;

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  const handleExtendSession = () => {
    extendSession();
    setShowModal(false);
  };

  return (
    <Dialog open={showModal} onOpenChange={setShowModal}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <svg
              className="size-5 text-amber-500"
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
            {t("sessionExpiringTitle")}
          </DialogTitle>
          <DialogDescription>
            {t("sessionExpiringDesc", {
              time: formatTime(secondsRemaining),
            })}
          </DialogDescription>
        </DialogHeader>
        <div className="py-4">
          <div className="flex items-center justify-center">
            <div className="text-4xl font-bold text-amber-500 tabular-nums">
              {formatTime(secondsRemaining)}
            </div>
          </div>
          <p className="text-center text-sm text-neutral-500 dark:text-neutral-400 mt-2">
            {t("sessionExpiringHint")}
          </p>
        </div>
        <DialogFooter className="flex-col sm:flex-row gap-2">
          <Button variant="outline" onClick={() => setShowModal(false)} className="w-full sm:w-auto">
            {t("logoutNow")}
          </Button>
          <Button onClick={handleExtendSession} className="w-full sm:w-auto">
            {t("extendSession")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
