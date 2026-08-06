"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

type TabKey = "songs" | "voiceTags";

const TABS: { key: TabKey; href: string; i18nKey: string }[] = [
  { key: "songs", href: "/dashboard/songs", i18nKey: "songs" },
  { key: "voiceTags", href: "/dashboard/voice-tags", i18nKey: "voiceTags" },
];

const TAB_ACTIONS: Record<TabKey, { href: string; i18nKey: string }> = {
  songs: { href: "/dashboard/songs/new", i18nKey: "uploadBtn" },
  voiceTags: { href: "/dashboard/voice-tags/new", i18nKey: "createBtn" },
};

function resolveActiveTab(pathname: string): TabKey {
  if (pathname.startsWith("/dashboard/voice-tags")) return "voiceTags";
  return "songs";
}

interface DashboardHeaderProps {
  itemCount?: number | null;
}

export function DashboardHeader({ itemCount }: DashboardHeaderProps) {
  const t = useTranslations("dashboard.voiceLibrary");
  const tList = useTranslations("voice.list");
  const pathname = usePathname();
  const activeTab = resolveActiveTab(pathname);
  const action = TAB_ACTIONS[activeTab];

  const countKey = activeTab === "songs" ? "songCount" : "voiceTagCount";

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between font-sans border-b border-neutral-200 pb-3 dark:border-neutral-800">
      <div className="flex items-center gap-2">
        <div className="flex h-9 items-center gap-1 rounded-t-lg bg-neutral-100 p-1 dark:bg-neutral-900 border border-b-0 border-neutral-200 dark:border-neutral-800">
          {TABS.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <Link
                key={tab.key}
                href={tab.href}
                aria-current={isActive ? "page" : undefined}
                className={`relative flex h-7 items-center justify-center rounded-md px-4 text-xs font-semibold tracking-wide transition-all active:translate-y-[1px] ${
                  isActive
                    ? "bg-white text-black shadow-sm dark:bg-black dark:text-white dark:shadow-neutral-900 border border-neutral-200/80 dark:border-neutral-800"
                    : "text-neutral-500 hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-neutral-100"
                }`}
              >
                {isActive && (
                  <span
                    className="absolute -bottom-1 left-1/2 h-[3px] w-5 -translate-x-1/2 rounded-full bg-black dark:bg-white"
                    aria-hidden="true"
                  />
                )}
                {t(`tabs.${tab.i18nKey}`)}
              </Link>
            );
          })}
        </div>

        <div className="hidden md:flex items-center gap-0.5 px-2 py-1.5 opacity-40 select-none" aria-hidden="true">
          <span className="h-4 w-1.5 rounded-sm bg-neutral-900 dark:bg-neutral-100" />
          <span className="h-4 w-1.5 rounded-sm bg-neutral-900 dark:bg-neutral-100" />
          <span className="h-4 w-1.5 rounded-sm bg-neutral-900 dark:bg-neutral-100" />
          <span className="h-2 w-1.5" />
          <span className="h-4 w-1.5 rounded-sm bg-neutral-900 dark:bg-neutral-100" />
          <span className="h-4 w-1.5 rounded-sm bg-neutral-900 dark:bg-neutral-100" />
        </div>
      </div>

      <div className="flex items-center justify-between sm:justify-end gap-4">
        {itemCount != null && (
          <span className="text-xs font-mono font-medium text-neutral-500 dark:text-neutral-400 uppercase tracking-wider">
            {tList(countKey, { count: itemCount })}
          </span>
        )}
        <Link href={action.href}>
          <Button
            size="sm"
            className="h-8 min-h-[44px] sm:min-h-0 rounded-md bg-black px-4 text-xs font-semibold text-white transition-all hover:bg-neutral-800 active:translate-y-[1px] dark:bg-white dark:text-black dark:hover:bg-neutral-200 border border-neutral-900 dark:border-neutral-100"
          >
            {t(action.i18nKey)}
          </Button>
        </Link>
      </div>
    </div>
  );
}
