"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useSessions, useRevokeSession, useRevokeAllOtherSessions } from "../api/sessions";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "sonner";

export function SessionsTable() {
  const t = useTranslations("sessions");
  const locale = useLocale();
  const { data: response, isLoading, isError } = useSessions();
  const { mutate: revokeMutate, isPending: isRevokingOne } = useRevokeSession();
  const { mutate: revokeAllMutate, isPending: isRevokingAll } = useRevokeAllOtherSessions();
  const [confirmOpen, setConfirmOpen] = useState(false);

  const isPending = isRevokingOne || isRevokingAll;

  const handleRevoke = (tokenUuid: string) => {
    revokeMutate(
      { tokenUuid },
      {
        onSuccess: (res) => {
          if (res.success) toast.success(t("revokeSuccess"));
          else toast.error(res.message || t("revokeError"));
        },
        onError: (err: any) => {
          toast.error(err?.message || t("revokeError"));
        },
      }
    );
  };

  const handleConfirmRevokeAll = () => {
    revokeAllMutate(undefined, {
      onSuccess: (res) => {
        if (res.success) toast.success(t("revokeAllSuccess"));
        else toast.error(res.message || t("revokeAllError"));
        setConfirmOpen(false);
      },
      onError: (err: any) => {
        toast.error(err?.message || t("revokeAllError"));
        setConfirmOpen(false);
      },
    });
  };

  const dateFormatter = new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });

  const formatRelativeTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffMins < 1) return t("timeJustNow");
      if (diffMins < 60) return t("timeMinutes", { minutes: diffMins });
      if (diffHours < 24) return t("timeHours", { hours: diffHours });
      if (diffDays < 30) return t("timeDays", { days: diffDays });

      return dateFormatter.format(date);
    } catch {
      return "—";
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col gap-4 animate-pulse font-sans">
        <div className="h-6 w-48 bg-neutral-200 dark:bg-neutral-800 rounded" />
        <div className="h-10 w-full bg-neutral-200 dark:bg-neutral-800 rounded" />
        <div className="space-y-3">
          {Array(3)
            .fill(null)
            .map((_, i) => (
              <div key={i} className="h-16 w-full bg-neutral-200 dark:bg-neutral-800 rounded" />
            ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-lg bg-neutral-100 p-6 text-center text-sm font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200 font-sans">
        {t("loadError")}
      </div>
    );
  }

  const sessions = response?.data || [];
  const hasOtherSessions = sessions.some((s) => !s.isCurrent);

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-black dark:text-white">
            {t("title")}
          </h2>
          <p className="text-xs text-neutral-400 mt-1">{t("subtitle")}</p>
        </div>
        {hasOtherSessions && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setConfirmOpen(true)}
            disabled={isPending}
            className="w-full sm:w-auto text-xs font-semibold border-neutral-200 hover:bg-neutral-50 dark:border-neutral-800 dark:hover:bg-neutral-900"
          >
            {t("revokeAll")}
          </Button>
        )}
      </div>

      <div className="hidden lg:block overflow-hidden rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black">
        <table className="w-full text-left text-sm">
          <thead className="bg-neutral-50 dark:bg-neutral-950 text-xs font-semibold text-neutral-400 border-b border-neutral-200 dark:border-neutral-800">
            <tr>
              <th className="px-6 py-3.5">{t("thDevice")}</th>
              <th className="px-6 py-3.5">{t("thIp")}</th>
              <th className="px-6 py-3.5">{t("thLocation")}</th>
              <th className="px-6 py-3.5">{t("thCreated")}</th>
              <th className="px-6 py-3.5 text-right">{t("thAction")}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-200 dark:divide-neutral-800">
            {sessions.map((session) => (
              <tr key={session.sessionUuid} className="hover:bg-neutral-50/50 dark:hover:bg-neutral-950/50">
                <td className="px-6 py-4 font-medium text-black dark:text-white flex items-center gap-2">
                  <span>{session.deviceInfo || "—"}</span>
                  {session.isCurrent && (
                    <span className="rounded bg-neutral-100 dark:bg-neutral-900 px-1.5 py-0.5 text-[10px] font-bold text-neutral-600 dark:text-neutral-300">
                      {t("currentBadge")}
                    </span>
                  )}
                </td>
                <td className="px-6 py-4 text-neutral-500">{session.ipAddress}</td>
                <td className="px-6 py-4 text-neutral-500">{session.location || "—"}</td>
                <td className="px-6 py-4 text-neutral-500">{formatRelativeTime(session.createdAt)}</td>
                <td className="px-6 py-4 text-right">
                  {!session.isCurrent && (
                    <button
                      disabled={isPending}
                      onClick={() => handleRevoke(session.sessionUuid)}
                      className="text-xs font-bold text-neutral-500 hover:text-black dark:hover:text-white hover:underline focus:outline-none disabled:opacity-50"
                    >
                      {t("revokeButton")}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col gap-4 lg:hidden">
        {sessions.map((session) => (
          <div
            key={session.sessionUuid}
            className="flex flex-col gap-3 rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black p-4 shadow-sm"
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-black dark:text-white">
                {session.deviceInfo || "—"}
              </span>
              {session.isCurrent ? (
                <span className="rounded bg-neutral-100 dark:bg-neutral-900 px-2 py-0.5 text-[10px] font-bold text-neutral-600 dark:text-neutral-300">
                  {t("currentMobileBadge")}
                </span>
              ) : (
                <button
                  disabled={isPending}
                  onClick={() => handleRevoke(session.sessionUuid)}
                  className="text-xs font-bold text-neutral-500 hover:text-black dark:hover:text-white focus:outline-none min-h-[36px] px-2 flex items-center"
                >
                  {t("revokeMobileButton")}
                </button>
              )}
            </div>

            <hr className="border-neutral-100 dark:border-neutral-900" />

            <div className="grid grid-cols-2 gap-y-2 text-xs text-neutral-500">
              <div>{t("thIp")}:</div>
              <div className="text-right font-medium text-neutral-800 dark:text-neutral-200">
                {session.ipAddress}
              </div>
              <div>{t("thLocation")}:</div>
              <div className="text-right font-medium text-neutral-800 dark:text-neutral-200">
                {session.location || "—"}
              </div>
              <div>{t("thCreated")}:</div>
              <div className="text-right font-medium text-neutral-800 dark:text-neutral-200">
                {formatRelativeTime(session.createdAt)}
              </div>
            </div>
          </div>
        ))}
      </div>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("revokeAll")}</DialogTitle>
            <DialogDescription>{t("revokeAllConfirm")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex-col sm:flex-row gap-2">
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={isPending}>
              {t("cancel")}
            </Button>
            <Button onClick={handleConfirmRevokeAll} disabled={isPending}>
              {t("revokeAll")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}