"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useSessionTimeout, formatRemainingTime } from "@/hooks/use-session-timeout";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useLogout } from "@/features/auth/api/account";
import { useRouter } from "next/navigation";

interface SessionTimeoutWarningProps {
  onSessionExpired?: () => void;
  onRefreshSuccess?: () => void;
}

export function SessionTimeoutWarning({ onSessionExpired, onRefreshSuccess }: SessionTimeoutWarningProps = {}) {
  const t = useTranslations("auth.session");
  const router = useRouter();
  const { mutate: logoutMutate } = useLogout();
  const accessTokenExpiresAt = useAuthStore((s) => s.accessTokenExpiresAt);
  const { shouldShowExpired, refreshFailed, isRefreshing, remainingMs } = useSessionTimeout({
    onSessionExpired: () => {
      onSessionExpired?.();
    },
    onRefreshSuccess: () => {
      onRefreshSuccess?.();
    },
  });
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    setCountdown(Math.ceil(remainingMs / 1000));
  }, [remainingMs]);

  if (!accessTokenExpiresAt) return null;

  const handleLoginAgain = () => {
    useAuthStore.getState().clearAuth();
    const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
    router.push(`/login?returnTo=${returnTo}`);
  };

  return (
    <Dialog open={shouldShowExpired} onOpenChange={() => {}}>
      <DialogContent showCloseButton={false} onPointerDownOutside={(e) => e.preventDefault()} onEscapeKeyDown={(e) => e.preventDefault()}>
        <DialogHeader>
          <DialogTitle>{refreshFailed ? t("sessionExpiredTitle") : t("refreshingTitle")}</DialogTitle>
          <DialogDescription>
            {refreshFailed
              ? t("sessionExpiredDesc")
              : t("refreshingDesc")}
          </DialogDescription>
        </DialogHeader>
        {refreshFailed && (
          <>
            <p className="text-sm text-neutral-600 dark:text-neutral-400">{t("sessionExpiredHint")}</p>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button variant="outline" onClick={handleLoginAgain}>
                {t("loginAgain")}
              </Button>
            </DialogFooter>
          </>
        )}
        {!refreshFailed && (
          <div className="flex items-center justify-center py-4">
            <div className="text-center">
              <div className="text-3xl font-mono font-bold text-primary">{formatRemainingTime(remainingMs)}</div>
              <div className="text-sm text-muted-foreground mt-1">
                {isRefreshing ? t("refreshingInProgress") : t("autoRefreshing")}
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
