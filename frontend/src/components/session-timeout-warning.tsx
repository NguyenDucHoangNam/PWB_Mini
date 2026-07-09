"use client";

import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useSessionTimeout, formatRemainingTime } from "@/hooks/use-session-timeout";
import { useLogout } from "@/features/auth/api/account";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useRouter } from "next/navigation";

interface SessionTimeoutWarningProps {
  onLogout?: () => void;
}

export function SessionTimeoutWarning({ onLogout }: SessionTimeoutWarningProps = {}) {
  const t = useTranslations("auth.session");
  const router = useRouter();
  const { mutate: logoutMutate } = useLogout();
  const accessTokenExpiresAt = useAuthStore((s) => s.accessTokenExpiresAt);

  const { showWarning, remainingMs, extend, logout } = useSessionTimeout({
    onLogout: () => {
      if (onLogout) {
        onLogout();
        return;
      }
      logoutMutate(undefined, {
        onSettled: () => {
          useAuthStore.getState().clearAuth();
          router.push("/login");
        },
      });
    },
  });

  // Nothing to show if no expiry is known.
  if (!accessTokenExpiresAt) return null;

  return (
    <Dialog
      open={showWarning}
      onOpenChange={(open) => {
        // The dialog should not be dismissible by backdrop; user must
        // explicitly choose to extend or log out.
        if (!open) return;
      }}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t("sessionExpiringTitle")}</DialogTitle>
          <DialogDescription>
            {t("sessionExpiringDesc", { time: formatRemainingTime(remainingMs) })}
          </DialogDescription>
        </DialogHeader>
        <p className="text-sm text-neutral-600 dark:text-neutral-400">
          {t("sessionExpiringHint")}
        </p>
        <DialogFooter className="flex-col sm:flex-row gap-2">
          <Button variant="outline" onClick={logout}>
            {t("logoutNow")}
          </Button>
          <Button onClick={() => void extend()}>{t("extendSession")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}