"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
import { abortRefresh } from "@/lib/auth-refresh";
import { useEffect } from "react";

export default function UnauthorizedPage() {
  const t = useTranslations("errors.401");

  // Clear auth state and broadcast logout to sync across tabs.
  useEffect(() => {
    abortRefresh();
    useAuthStore.getState().clearAuth();
    broadcastAuthMessage({ type: "LOGOUT" });
  }, []);

  return (
    <div className="flex flex-col items-center text-center">
      {/* Background Status Code */}
      <span className="text-8xl font-extrabold tracking-widest text-neutral-200 dark:text-neutral-800 select-none">
        401
      </span>

      {/* Title */}
      <h1 className="mt-4 text-2xl font-bold tracking-tight text-black dark:text-white">
        {t("title")}
      </h1>

      {/* Icon & Description */}
      <div className="mt-6 flex size-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900 text-neutral-500">
        <svg
          className="size-8"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
          />
        </svg>
      </div>

      <p className="mt-6 max-w-sm text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
        {t("desc")}
      </p>

      {/* CTA */}
      <Link href="/login" className="mt-8 w-full">
        <Button variant="default" size="lg" className="w-full h-11 text-sm font-semibold">
          {t("btn")}
        </Button>
      </Link>
    </div>
  );
}
