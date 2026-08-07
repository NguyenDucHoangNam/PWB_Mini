"use client";

import { SiteHeader } from "@/components/layout/site-header";

interface AuthLayoutProps {
  children: React.ReactNode;
}

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-white dark:bg-black font-sans">
      <SiteHeader />
      <div className="flex flex-1 w-full flex-col justify-center items-center px-5 py-10 sm:px-6 sm:py-12 md:px-8">
        <div className="w-full max-w-[480px]">{children}</div>
      </div>
    </div>
  );
}
