"use client";

import { useTranslations } from "next-intl";

interface AuthLayoutProps {
  children: React.ReactNode;
}

export function AuthLayout({ children }: AuthLayoutProps) {
  const t = useTranslations("auth.layout");

  return (
    <div className="flex min-h-[calc(100vh-64px)] w-full font-sans">
      {/* Left Column: Graphic/Branding Banner (Desktop only) */}
      <div className="hidden w-1/2 flex-col justify-between border-r border-neutral-200 bg-neutral-50 p-12 lg:flex dark:border-neutral-800 dark:bg-neutral-950">
        <div className="flex flex-col gap-4">
          <span className="text-2xl font-bold tracking-tight text-black dark:text-white">
            PWB MiNi
          </span>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {t("description")}
          </p>
        </div>

        {/* Abstract Monochrome Pattern */}
        <div className="my-8 flex flex-1 items-center justify-center">
          <div className="relative size-64 select-none opacity-40 dark:opacity-20">
            {/* Grid Pattern */}
            <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808012_1px,transparent_1px),linear-gradient(to_bottom,#80808012_1px,transparent_1px)] bg-[size:24px_24px]" />
            {/* Concentric circles resembling a speaker/vinyl */}
            <div className="absolute inset-0 m-auto size-48 rounded-full border border-neutral-300 dark:border-neutral-700 animate-pulse" />
            <div className="absolute inset-0 m-auto size-32 rounded-full border border-neutral-400 dark:border-neutral-600" />
            <div className="absolute inset-0 m-auto size-16 rounded-full border border-neutral-600 dark:border-neutral-400" />
            <div className="absolute inset-0 m-auto size-4 rounded-full bg-black dark:bg-white" />
          </div>
        </div>

        <div className="text-xs text-neutral-400">
          {t("footer")}
        </div>
      </div>

      {/* Right Column: Form Container */}
      <div className="flex w-full flex-col justify-center px-6 py-12 lg:w-1/2 md:px-12 bg-white dark:bg-black">
        <div className="mx-auto w-full max-w-[420px]">{children}</div>
      </div>
    </div>
  );
}
