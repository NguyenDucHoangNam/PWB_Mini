"use client";

import { SiteHeader } from "@/components/layout/site-header";

interface AuthLayoutProps {
  children: React.ReactNode;
}

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-[#e0e5ec] font-sans transition-colors dark:bg-[#1e222b]">
      <SiteHeader />
      <div className="flex min-h-0 w-full flex-1 overflow-y-auto">
        <div className="m-auto w-full px-4 py-6 sm:px-6 sm:py-8 md:px-8">
          <div className="neu-raised mx-auto w-full max-w-[440px] rounded-3xl border-none p-5 has-[[data-auth-wide]]:max-w-[760px] sm:p-7">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
