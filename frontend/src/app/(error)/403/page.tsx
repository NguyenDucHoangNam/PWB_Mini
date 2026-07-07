"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function ForbiddenPage() {
  const t = useTranslations("errors.403");

  return (
    <div className="flex flex-col items-center text-center">
      {/* Background Status Code */}
      <span className="text-8xl font-extrabold tracking-widest text-neutral-200 dark:text-neutral-800 select-none">
        403
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
            d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"
          />
        </svg>
      </div>

      <p className="mt-6 max-w-sm text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
        {t("desc")}
      </p>

      {/* CTA */}
      <Link href="/dashboard" className="mt-8 w-full">
        <Button variant="default" size="lg" className="w-full h-11 text-sm font-semibold">
          {t("btn")}
        </Button>
      </Link>
    </div>
  );
}
