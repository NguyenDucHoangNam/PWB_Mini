"use client";

import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Mic, Music } from "lucide-react";
import { PageHeader, type PageHeaderTab } from "@/components/layout/page-header";

type TabKey = "songs" | "voiceTags";

const TAB_ICONS: Record<TabKey, PageHeaderTab["icon"]> = {
  songs: Music,
  voiceTags: Mic,
};

const TABS: { key: TabKey; href: string }[] = [
  { key: "songs", href: "/dashboard/songs" },
  { key: "voiceTags", href: "/dashboard/voice-tags" },
];

function resolveActiveTab(pathname: string): TabKey {
  if (pathname.startsWith("/dashboard/voice-tags")) return "voiceTags";
  return "songs";
}

export function DashboardHeader() {
  const t = useTranslations("dashboard.voiceLibrary");
  const tSongs = useTranslations("voice.songs");
  const tVoiceTags = useTranslations("voice.voiceTags");
  const pathname = usePathname();

  const activeTab = resolveActiveTab(pathname);
  const heading = activeTab === "songs" ? tSongs("title") : tVoiceTags("title");
  const subtitle = activeTab === "songs" ? tSongs("subtitle") : tVoiceTags("subtitle");

  return (
    <PageHeader
      title={heading}
      subtitle={subtitle}
      icon={activeTab === "songs" ? Music : Mic}
      activeTabKey={activeTab}
      tabs={TABS.map((tab) => ({
        key: tab.key,
        href: tab.href,
        label: t(`tabs.${tab.key}`),
        icon: TAB_ICONS[tab.key],
      }))}
    />
  );
}
