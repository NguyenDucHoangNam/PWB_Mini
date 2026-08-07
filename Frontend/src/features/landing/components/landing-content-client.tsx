"use client";

import dynamic from "next/dynamic";

const LandingContent = dynamic(
  () =>
    import("@/features/landing/components/landing-content").then((m) => ({
      default: m.LandingContent,
    })),
  {
    ssr: false,
    loading: () => (
      <div className="flex min-h-[calc(100dvh-4.5rem)] w-full items-center justify-center bg-neutral-50 dark:bg-neutral-950">
        <div className="size-6 rounded-full border-2 border-neutral-200 border-t-black animate-spin dark:border-neutral-800 dark:border-t-white" />
      </div>
    ),
  },
);

export function LandingContentClient() {
  return <LandingContent />;
}
