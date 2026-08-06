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
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between font-sans">
      <div className="flex items-center gap-1 border-b border-neutral-200 dark:border-neutral-800">
        {TABS.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <Link
              key={tab.key}
              href={tab.href}
              aria-current={isActive ? "page" : undefined}
              className={`relative -mb-px border-b-2 px-4 py-2 text-sm font-semibold transition-colors ${
                isActive
                  ? "border-black text-black dark:border-white dark:text-white"
                  : "border-transparent text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
              }`}
            >
              {t(`tabs.${tab.i18nKey}`)}
            </Link>
          );
        })}
      </div>

      <div className="flex items-center gap-3">
        {itemCount != null && (
          <span className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
            {tList(countKey, { count: itemCount })}
          </span>
        )}
        <Link href={action.href}>
          <Button size="sm">{t(action.i18nKey)}</Button>
        </Link>
      </div>
    </div>
  );
}
