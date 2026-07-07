"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useAuthStore } from "../stores/use-auth-store";
import { useLogout } from "../api/account";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";

export function AccountRecoveryPage() {
  const t = useTranslations("account.recovery");
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const setAuth = useAuthStore((state) => state.setAuth);
  const accessToken = useAuthStore((state) => state.accessToken);
  const { mutate: logoutMutate } = useLogout();

  const [isRecovering, setIsRecovering] = useState(false);
  const [daysLeft, setDaysLeft] = useState(24);
  const [error, setError] = useState<string | null>(null);

  const isUrgent = daysLeft <= 3;

  const handleCancelDeletion = () => {
    setIsRecovering(true);
    setError(null);

    // Simulate API call to cancel deletion
    setTimeout(() => {
      setIsRecovering(false);
      if (user && accessToken) {
        // Re-write Zustand auth store user status to ACTIVE to mock recovery status locally
        const updatedUser = { ...user, status: "ACTIVE" };
        setAuth(accessToken, updatedUser);
        toast.success(t("toastSuccess"));
        router.push("/dashboard");
      } else {
        toast.error(t("toastError"));
        router.push("/login");
      }
    }, 1500);
  };

  const handleLogoutClick = () => {
    logoutMutate(undefined, {
      onSuccess: () => {
        toast.success(t("logoutSuccess"));
        router.push("/login");
      },
      onError: () => {
        // Fallback clear auth on error
        useAuthStore.getState().clearAuth();
        router.push("/login");
      },
    });
  };

  return (
    <div className="flex flex-col items-center text-center font-sans">
      {/* Warning Icon */}
      <div className="mx-auto flex size-20 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900 text-neutral-800 dark:text-neutral-200 animate-pulse">
        <svg
          className="size-10"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>

      <div className="flex flex-col gap-2 mt-6">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400 max-w-md">
          {t("subtitle")}
        </p>
      </div>

      {/* Days left badge */}
      <div className="my-8">
        <div
          className={`inline-flex items-center gap-1.5 px-6 py-3.5 rounded-xl border text-xl font-extrabold tracking-tight ${
            isUrgent
              ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
              : "border-neutral-200 bg-neutral-50 text-black dark:border-neutral-800 dark:bg-neutral-900 dark:text-white"
          }`}
        >
          {t("daysLeft", { days: daysLeft })}
        </div>
      </div>

      {error && (
        <div role="alert" className="rounded-lg bg-neutral-100 p-3 text-xs font-semibold text-neutral-800 dark:bg-neutral-900 dark:text-neutral-200 mb-4 w-full max-w-sm">
          {error}
        </div>
      )}

      {/* Action Buttons */}
      <div className="flex w-full max-w-sm flex-col gap-3">
        <Button
          onClick={handleCancelDeletion}
          disabled={isRecovering}
          variant="default"
          size="lg"
          className="w-full h-12 text-sm font-semibold"
        >
          {isRecovering ? (
            <span className="flex items-center gap-2 justify-center">
              <svg className="animate-spin size-4 text-white dark:text-black" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
              {t("submitting")}
            </span>
          ) : (
            t("submit")
          )}
        </Button>

        <Button
          onClick={handleLogoutClick}
          variant="ghost"
          size="lg"
          className="w-full h-10 text-xs font-semibold text-neutral-400 hover:text-black dark:hover:text-white"
        >
          {t("logout")}
        </Button>
      </div>
    </div>
  );
}
