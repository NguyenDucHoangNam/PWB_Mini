"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { MinimalLayout } from "@/components/layout/minimal-layout";
import { Button } from "@/components/ui/button";

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("errors.500");

  useEffect(() => {
    console.error("Application error boundary caught:", error);
  }, [error]);

  return (
    <MinimalLayout>
      <div className="flex flex-col items-center text-center">
        {/* Background Status Code */}
        <span className="text-8xl font-extrabold tracking-widest text-neutral-200 dark:text-neutral-800 select-none">
          500
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
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <p className="mt-6 max-w-sm text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
          {t("desc")}
        </p>

        {/* Action Buttons */}
        <div className="mt-8 flex w-full flex-col gap-3">
          <Button
            onClick={() => reset()}
            variant="default"
            size="lg"
            className="w-full h-11 text-sm font-semibold"
          >
            {t("btn")}
          </Button>
          <a href="mailto:support@pwbmini.com" className="w-full">
            <Button
              variant="outline"
              size="lg"
              className="w-full h-11 text-sm font-semibold"
            >
              {t("contact")}
            </Button>
          </a>
        </div>
      </div>
    </MinimalLayout>
  );
}
