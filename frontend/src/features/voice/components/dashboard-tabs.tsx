"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { DashboardSongsTab } from "./dashboard-songs-tab";
import { DashboardVoiceTagsTab } from "./dashboard-voice-tags-tab";

type TabKey = "songs" | "voiceTags";

const TABS: { key: TabKey; i18nKey: string }[] = [
  { key: "songs", i18nKey: "songs" },
  { key: "voiceTags", i18nKey: "voiceTags" },
];

const TAB_ACTIONS: Record<TabKey, { href: string; i18nKey: string }> = {
  songs: { href: "/dashboard/songs/new", i18nKey: "uploadBtn" },
  voiceTags: { href: "/dashboard/voice-tags/new", i18nKey: "createBtn" },
};

export function DashboardTabs() {
  const t = useTranslations("dashboard.voiceLibrary");
  const [activeTab, setActiveTab] = useState<TabKey>("songs");

  const action = TAB_ACTIONS[activeTab];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex items-center gap-1 border-b border-neutral-200 dark:border-neutral-800">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              aria-pressed={activeTab === tab.key}
              className={`relative -mb-px border-b-2 px-4 py-2 text-sm font-semibold transition-colors ${
                activeTab === tab.key
                  ? "border-black text-black dark:border-white dark:text-white"
                  : "border-transparent text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
              }`}
            >
              {t(`tabs.${tab.i18nKey}`)}
            </button>
          ))}
        </div>
        <Link href={action.href}>
          <Button className="self-start sm:self-auto">{t(action.i18nKey)}</Button>
        </Link>
      </div>

      {activeTab === "songs" ? <DashboardSongsTab /> : <DashboardVoiceTagsTab />}
    </div>
  );
}