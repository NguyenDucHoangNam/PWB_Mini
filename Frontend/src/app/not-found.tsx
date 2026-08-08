"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { MinimalLayout } from "@/components/layout/minimal-layout";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

export default function NotFound() {
  const t = useTranslations("errors.404");
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());

  return (
    <MinimalLayout>
      <div className="flex flex-col items-center text-center">
        {/* Background Status Code */}
        <span className="text-8xl font-extrabold tracking-widest text-neutral-200 dark:text-neutral-800 select-none">
          404
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
              d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>

        <p className="mt-6 max-w-sm text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
          {t("desc")}
        </p>

        {/* CTA */}
        <Link href={isAuthenticated ? "/" : "/"} className="mt-8 w-full">
          <Button variant="default" size="lg" className="w-full h-11 text-sm font-semibold">
            {t("btn")}
          </Button>
        </Link>
      </div>
    </MinimalLayout>
  );
}
